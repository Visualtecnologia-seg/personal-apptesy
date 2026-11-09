package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Para onde o dinheiro do profissional pode ir: só para o CPF da conta ou para o CNPJ (MEI) dele, conferido.
 * Vale para a chave PIX e para a conta bancária.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

  private final JdbcTemplate jdbc;
  private final SecurityService securityService;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

  @Value("${CNPJ_LOOKUP_URL:https://brasilapi.com.br/api/cnpj/v1/}")
  private String lookupUrl;

  public static final Map<String, String> BANKS = new LinkedHashMap<>();
  static {
    BANKS.put("001", "Banco do Brasil"); BANKS.put("237", "Bradesco"); BANKS.put("104", "Caixa");
    BANKS.put("341", "Itaú"); BANKS.put("033", "Santander"); BANKS.put("260", "Nubank");
    BANKS.put("077", "Inter"); BANKS.put("336", "C6 Bank"); BANKS.put("290", "PagBank");
    BANKS.put("323", "Mercado Pago"); BANKS.put("380", "PicPay"); BANKS.put("748", "Sicredi");
    BANKS.put("756", "Sicoob"); BANKS.put("212", "Banco Original"); BANKS.put("208", "BTG Pactual");
    BANKS.put("041", "Banrisul"); BANKS.put("070", "BRB"); BANKS.put("004", "Banco do Nordeste");
    BANKS.put("403", "Cora"); BANKS.put("197", "Stone"); BANKS.put("461", "Asaas");
  }

  public static String digits(String s) { return s == null ? "" : s.replaceAll("\\D", ""); }

  public static String cpfOf(User u) {
    String d = digits(u == null ? null : u.getCpf());
    return d.length() == 11 ? d : null;
  }

  /** CNPJ conferido da pessoa (ou null). */
  public String verifiedCnpj(Long userId) {
    if (userId == null) return null;
    return jdbc.query("SELECT cnpj FROM user_ WHERE id = ? AND cnpj_verified_at IS NOT NULL", rs -> rs.next() ? rs.getString(1) : null, userId);
  }

  /** "CPF" ou "CNPJ" se o documento for da própria pessoa; null caso contrário. */
  public String ownDocType(User u, String doc) {
    String d = digits(doc);
    if (d.isEmpty() || u == null) return null;
    if (d.equals(cpfOf(u))) return "CPF";
    String cnpj = verifiedCnpj(u.getId());
    if (cnpj != null && d.equals(cnpj)) return "CNPJ";
    return null;
  }

  /** A chave PIX atual pode receber? (é o CPF ou o CNPJ conferido). */
  public boolean pixOk(User u) {
    return u != null && u.getPixKey() != null && ownDocType(u, u.getPixKey()) != null;
  }

  public Map<String, Object> bank(Long userId) {
    List<Map<String, Object>> l = jdbc.queryForList("SELECT bank_code, bank_name, agency, account, account_digit, account_type, owner_doc, owner_name FROM bank_account_ WHERE user_id = ?", userId);
    return l.isEmpty() ? null : l.get(0);
  }

  /** A conta bancária cadastrada ainda está no CPF ou no CNPJ conferido da pessoa? */
  public boolean bankOk(User u, Map<String, Object> b) {
    return b != null && ownDocType(u, Objects.toString(b.get("owner_doc"), "")) != null;
  }

  // ───────────── CNPJ ─────────────

  public static boolean cnpjValid(String c) {
    String d = digits(c);
    if (d.length() != 14 || d.chars().distinct().count() == 1) return false;
    int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}, w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    return dv(d, w1) == d.charAt(12) - '0' && dv(d, w2) == d.charAt(13) - '0';
  }

  private static int dv(String d, int[] w) {
    int s = 0;
    for (int i = 0; i < w.length; i++) s += (d.charAt(i) - '0') * w[i];
    int r = s % 11;
    return r < 2 ? 0 : 11 - r;
  }

  private static String norm(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    return n.replaceAll("[^A-Z ]", " ").replaceAll("\\s+", " ").trim();
  }

  /**
   * Confere na Receita (consulta gratuita) que o CNPJ está ATIVO e é da pessoa: o CPF aparece no nome da empresa
   * (padrão do MEI), ou o nome da empresa é o nome da pessoa, ou ela é sócia com o CPF batendo.
   */
  public Map<String, Object> verifyCnpj(User u, String raw) {
    String cnpj = digits(raw);
    if (!cnpjValid(cnpj)) throw new IllegalArgumentException("CNPJ inválido. Confira os números.");
    String cpf = cpfOf(u);
    if (cpf == null) throw new IllegalArgumentException("Cadastre o seu CPF antes de adicionar o CNPJ.");
    Integer other = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE cnpj = ? AND id <> ?", Integer.class, cnpj, u.getId());
    if (other != null && other > 0) throw new IllegalArgumentException("Este CNPJ já está em outra conta. Fale com o suporte se for seu.");
    JsonNode j;
    try {
      HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(lookupUrl + cnpj)).timeout(Duration.ofSeconds(12))
          .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
      if (r.statusCode() == 404) throw new IllegalArgumentException("Não encontramos este CNPJ na Receita Federal.");
      if (r.statusCode() != 200) throw new IllegalStateException("status " + r.statusCode());
      j = mapper.readTree(r.body());
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Consulta de CNPJ falhou: {}", e.getMessage());
      throw new IllegalArgumentException("Não conseguimos consultar o CNPJ agora. Tente de novo em alguns minutos.");
    }
    String situacao = j.path("descricao_situacao_cadastral").asText("");
    if (!"ATIVA".equalsIgnoreCase(situacao.trim())) throw new IllegalArgumentException("Este CNPJ não está ativo na Receita Federal.");
    String razao = j.path("razao_social").asText("");
    String name = norm(u.getName());
    boolean mine = digits(razao).contains(cpf) || (!name.isEmpty() && norm(razao).equals(name));
    if (!mine) {
      for (JsonNode s : j.path("qsa")) {
        String partnerDoc = digits(s.path("cnpj_cpf_do_socio").asText(""));
        boolean nameOk = !name.isEmpty() && norm(s.path("nome_socio").asText("")).equals(name);
        // A Receita mostra o CPF do sócio escondido (***123456**): os 6 do meio têm que bater
        boolean docOk = partnerDoc.length() == 6 ? cpf.substring(3, 9).equals(partnerDoc) : partnerDoc.equals(cpf);
        if (nameOk && docOk) { mine = true; break; }
      }
    }
    if (!mine) throw new IllegalArgumentException("Este CNPJ não está no seu nome. Só dá para usar o CNPJ (MEI) da própria pessoa da conta.");
    String company = razao.replaceAll("\\s*\\d{11}\\s*$", "").trim();
    if (company.isEmpty()) company = razao;
    String before = verifiedCnpj(u.getId());
    jdbc.update("UPDATE user_ SET cnpj = ?, cnpj_name = ?, cnpj_verified_at = ? WHERE id = ?", cnpj, company, Timestamp.valueOf(LocalDateTime.now()), u.getId());
    if (!cnpj.equals(before)) securityService.event(u.getId(), "CNPJ_CHANGED", "CNPJ ****" + cnpj.substring(8, 12) + "-" + cnpj.substring(12));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("cnpj", maskCnpj(cnpj));
    out.put("name", company);
    return out;
  }

  public void removeCnpj(User u) {
    String cnpj = verifiedCnpj(u.getId());
    jdbc.update("UPDATE user_ SET cnpj = NULL, cnpj_name = NULL, cnpj_verified_at = NULL WHERE id = ?", u.getId());
    if (cnpj != null) {
      // O que estava no CNPJ deixa de valer
      jdbc.update("UPDATE user_ SET pix_key = NULL, pix_key_type = NULL WHERE id = ? AND regexp_replace(COALESCE(pix_key,''), '\\D', '', 'g') = ?", u.getId(), cnpj);
      jdbc.update("DELETE FROM bank_account_ WHERE user_id = ? AND owner_doc = ?", u.getId(), cnpj);
      securityService.event(u.getId(), "CNPJ_CHANGED", "CNPJ removido");
    }
  }

  // ───────────── Conta bancária ─────────────

  public Map<String, Object> saveBank(User u, Map<String, Object> body) {
    String code = digits(Objects.toString(body.get("bankCode"), ""));
    if (code.length() > 0 && code.length() < 3) code = String.format("%03d", Integer.parseInt(code));
    String agency = digits(Objects.toString(body.get("agency"), ""));
    String account = digits(Objects.toString(body.get("account"), ""));
    String digit = Objects.toString(body.get("digit"), "").trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9X]", "");
    String type = Objects.toString(body.get("type"), "CONTA_CORRENTE");
    String owner = Objects.toString(body.get("owner"), "CPF");
    if (code.length() != 3) throw new IllegalArgumentException("Escolha o banco.");
    if (agency.isEmpty() || agency.length() > 5) throw new IllegalArgumentException("Agência inválida (sem o dígito).");
    if (account.isEmpty() || account.length() > 13) throw new IllegalArgumentException("Número da conta inválido.");
    if (digit.isEmpty() || digit.length() > 2) throw new IllegalArgumentException("Informe o dígito da conta.");
    if (!"CONTA_CORRENTE".equals(type) && !"CONTA_POUPANCA".equals(type)) throw new IllegalArgumentException("Tipo de conta inválido.");
    String doc, ownerName;
    if ("CNPJ".equals(owner)) {
      doc = verifiedCnpj(u.getId());
      if (doc == null) throw new IllegalArgumentException("Adicione e confirme o seu CNPJ antes.");
      ownerName = jdbc.queryForObject("SELECT cnpj_name FROM user_ WHERE id = ?", String.class, u.getId());
    } else {
      doc = cpfOf(u);
      if (doc == null) throw new IllegalArgumentException("Cadastre o seu CPF antes.");
      ownerName = u.getName();
    }
    String bankName = BANKS.getOrDefault(code, "Banco " + code);
    jdbc.update("INSERT INTO bank_account_ (user_id, bank_code, bank_name, agency, account, account_digit, account_type, owner_doc, owner_name, updated_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,NOW()) ON CONFLICT (user_id) DO UPDATE SET bank_code = EXCLUDED.bank_code, bank_name = EXCLUDED.bank_name, " +
            "agency = EXCLUDED.agency, account = EXCLUDED.account, account_digit = EXCLUDED.account_digit, account_type = EXCLUDED.account_type, " +
            "owner_doc = EXCLUDED.owner_doc, owner_name = EXCLUDED.owner_name, updated_at = NOW()",
        u.getId(), code, bankName, agency, account, digit, type, doc, ownerName);
    securityService.event(u.getId(), "BANK_CHANGED", bankName + " ag. " + agency + " conta ••••" + tail(account));
    return publicBank(u, bank(u.getId()));
  }

  public void removeBank(User u) {
    if (jdbc.update("DELETE FROM bank_account_ WHERE user_id = ?", u.getId()) > 0) securityService.event(u.getId(), "BANK_CHANGED", "Conta removida");
  }

  /** O que o app pode ver da conta (mascarado). */
  public Map<String, Object> publicBank(User u, Map<String, Object> b) {
    if (b == null) return null;
    Map<String, Object> o = new LinkedHashMap<>();
    o.put("bankCode", b.get("bank_code"));
    o.put("bankName", b.get("bank_name"));
    o.put("agency", b.get("agency"));
    o.put("account", "••••" + tail(Objects.toString(b.get("account"), "")) + "-" + b.get("account_digit"));
    o.put("type", b.get("account_type"));
    o.put("owner", ownDocType(u, Objects.toString(b.get("owner_doc"), "")));
    o.put("ownerName", b.get("owner_name"));
    o.put("ok", bankOk(u, b));
    return o;
  }

  public static String maskCnpj(String c) {
    String d = digits(c);
    if (d.length() != 14) return null;
    return "**." + d.substring(2, 5) + "." + d.substring(5, 8) + "/" + d.substring(8, 12) + "-**";
  }

  private static String tail(String s) { return s.length() <= 3 ? s : s.substring(s.length() - 3); }

  /** Tudo sobre o recebimento da pessoa, para as telas. */
  public Map<String, Object> summary(User u) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> c = jdbc.queryForList("SELECT cnpj, cnpj_name FROM user_ WHERE id = ? AND cnpj_verified_at IS NOT NULL", u.getId());
    if (c.isEmpty()) out.put("cnpj", null);
    else {
      Map<String, Object> cn = new LinkedHashMap<>();
      cn.put("cnpj", maskCnpj(Objects.toString(c.get(0).get("cnpj"), "")));
      cn.put("name", c.get(0).get("cnpj_name"));
      out.put("cnpj", cn);
    }
    out.put("bank", publicBank(u, bank(u.getId())));
    List<Map<String, Object>> banks = new ArrayList<>();
    BANKS.forEach((k, v) -> banks.add(Map.of("code", k, "name", v)));
    out.put("banks", banks);
    return out;
  }
}
