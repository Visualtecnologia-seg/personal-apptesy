package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.SecurityService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.WalletService;
import br.com.personalfighters.service.impl.AuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Segurança da conta (aparelhos, "não fui eu", sair de todos) e direitos da LGPD
 * (baixar meus dados, apagar minha conta). Vale para clientes e profissionais.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MeSecurityController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final UserService userService;
  private final JdbcTemplate jdbc;
  private final SecurityService security;
  private final AuthenticationService authenticationService;
  private final PasswordEncoder passwordEncoder;
  private final WalletService walletService;

  private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(Z)); }
  private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("error", m, "message", m)); }

  // ───────────── Segurança ─────────────

  @GetMapping("/me/security")
  public Map<String, Object> mySecurity(@RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> devs = jdbc.queryForList("SELECT device_id, device_name AS name, first_seen AS \"firstSeen\", last_seen AS \"lastSeen\" FROM user_device_ WHERE user_id = ? ORDER BY last_seen DESC LIMIT 20", me.getId());
    for (Map<String, Object> d : devs) { d.put("current", Objects.equals(d.get("device_id"), deviceId)); d.remove("device_id"); }
    out.put("devices", devs);
    out.put("events", jdbc.queryForList("SELECT kind, detail, created_at AS \"createdAt\" FROM security_event_ WHERE user_id = ? ORDER BY id DESC LIMIT 10", me.getId()));
    LocalDateTime hold = security.withdrawHoldUntil(me.getId());
    out.put("withdrawHoldUntil", hold == null ? null : hold.toString());
    return out;
  }

  /** "Não fui eu": derruba todos os acessos, segura saques por 24 h e avisa a equipe com prioridade. */
  @PostMapping("/me/security/not-me")
  @Transactional
  public Map<String, Object> notMe() {
    User me = userService.findAuthUser();
    authenticationService.revokeAll(me.getId());
    security.event(me.getId(), "NOT_ME", "A pessoa avisou que um acesso não foi ela");
    log.warn("Segurança: 'não fui eu' do usuário {}", me.getId());
    return Map.of("ok", true, "message", "Pronto. Desconectamos a sua conta de todos os celulares e seguramos saques por 24 horas. Entre de novo e troque a sua senha em \"Esqueci minha senha\".");
  }

  @PostMapping("/me/security/logout-all")
  public Map<String, Object> logoutAll() {
    User me = userService.findAuthUser();
    authenticationService.revokeAll(me.getId());
    security.event(me.getId(), "LOGOUT_ALL", "Saiu de todos os aparelhos");
    return Map.of("ok", true);
  }

  // ───────────── Caixa de avisos do app ─────────────

  @GetMapping("/me/notices")
  public Map<String, Object> notices(@RequestParam(defaultValue = "cliente") String app) {
    User me = userService.findAuthUser();
    String a = "pro".equals(app) ? "pro" : "cliente";
    Long unread = jdbc.queryForObject("SELECT COUNT(*) FROM user_notice_ WHERE user_id = ? AND app = ? AND read_at IS NULL", Long.class, me.getId(), a);
    return Map.of("unread", unread == null ? 0 : unread,
        "items", jdbc.queryForList("SELECT id, text, created_at AS \"createdAt\", read_at IS NOT NULL AS read FROM user_notice_ WHERE user_id = ? AND app = ? ORDER BY id DESC LIMIT 60", me.getId(), a));
  }

  @PostMapping("/me/notices/read")
  public Map<String, Object> readNotices(@RequestParam(defaultValue = "cliente") String app) {
    User me = userService.findAuthUser();
    jdbc.update("UPDATE user_notice_ SET read_at = ? WHERE user_id = ? AND app = ? AND read_at IS NULL", now(), me.getId(), "pro".equals(app) ? "pro" : "cliente");
    return Map.of("ok", true);
  }

  // ───────────── LGPD: baixar meus dados ─────────────

  /** Cria um link de uso único (10 minutos) para baixar os dados pelo navegador. */
  @PostMapping("/me/export/link")
  public Map<String, Object> exportLink() {
    User me = userService.findAuthUser();
    byte[] b = new byte[24]; new SecureRandom().nextBytes(b);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    jdbc.update("INSERT INTO data_export_token_ (token, user_id, expires_at) VALUES (?, ?, ?)", token, me.getId(), Timestamp.valueOf(LocalDateTime.now(Z).plusMinutes(10)));
    security.event(me.getId(), "DATA_EXPORT", "Pediu para baixar os dados");
    return Map.of("url", "/public/export/" + token);
  }

  @GetMapping("/public/export/{token}")
  public ResponseEntity<?> export(@PathVariable String token) {
    List<Map<String, Object>> t = jdbc.queryForList("SELECT user_id FROM data_export_token_ WHERE token = ? AND used_at IS NULL AND expires_at > ?", token, now());
    if (t.isEmpty()) return ResponseEntity.status(410).contentType(MediaType.TEXT_PLAIN).body("Este link já foi usado ou expirou. Peça um novo no app: Perfil → Meus dados.");
    jdbc.update("UPDATE data_export_token_ SET used_at = ? WHERE token = ?", now(), token);
    Long id = ((Number) t.get(0).get("user_id")).longValue();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("geradoEm", LocalDateTime.now(Z).toString());
    data.put("aviso", "Estes são os dados que a OPUS guarda sobre você (LGPD, art. 18).");
    section(data, "conta", "SELECT id, name AS nome, surname AS sobrenome, email, phone_number AS telefone, cpf, birthday AS nascimento, gender AS genero, full_address AS endereco, latitude, longitude, verified AS verificado, verification_status AS \"statusVerificacao\", pix_key_type AS \"tipoChavePix\" FROM user_ WHERE id = ?", id);
    section(data, "pedidosComoCliente", "SELECT o.id, o.date AS data, o.status, o.total_cost AS total, p.name AS servico FROM order_ o LEFT JOIN product_ p ON p.id = o.product_id WHERE o.customer_id = ? ORDER BY o.created_at DESC", id);
    section(data, "pedidosComoProfissional", "SELECT o.id, o.date AS data, o.status, o.total_cost AS total, p.name AS servico FROM order_ o LEFT JOIN product_ p ON p.id = o.product_id WHERE o.professional_id = ? ORDER BY o.created_at DESC", id);
    section(data, "avaliacoes", "SELECT rating AS nota, comment AS comentario, date AS data FROM review_ WHERE customer_id = ? OR professional_id = ?", id, id);
    section(data, "carteira", "SELECT kind AS tipo, amount AS valor, status, description AS descricao, created_at AS data FROM wallet_entry_ WHERE professional_id = ? ORDER BY created_at DESC", id);
    section(data, "publicacoes", "SELECT id, caption AS legenda, created_at AS data FROM post_ WHERE professional_id = ? ORDER BY created_at DESC", id);
    section(data, "suporte", "SELECT text AS mensagem, from_admin AS \"daOpus\", created_at AS data FROM support_message_ WHERE user_id = ? ORDER BY id", id);
    section(data, "aparelhos", "SELECT device_name AS aparelho, first_seen AS \"primeiroAcesso\", last_seen AS \"ultimoAcesso\" FROM user_device_ WHERE user_id = ?", id);
    section(data, "acessos", "SELECT ip_address AS ip, success AS sucesso, created_at AS data FROM login_history_ WHERE user_id = ? ORDER BY id DESC LIMIT 100", id);
    section(data, "favoritos", "SELECT professional_id AS profissional FROM favorite_ WHERE user_id = ?", id);
    section(data, "verificacaoDeIdentidade", "SELECT status, doc_type AS documento, submitted_at AS \"enviadoEm\" FROM identity_verification_ WHERE user_id = ?", id);
    try {
      byte[] json = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
      return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-opus.json\"")
          .contentType(new MediaType("application", "json", StandardCharsets.UTF_8)).body(json);
    } catch (Exception e) { return ResponseEntity.status(500).body("Não foi possível gerar o arquivo."); }
  }

  private void section(Map<String, Object> data, String name, String sql, Object... args) {
    try { data.put(name, jdbc.queryForList(sql, args)); } catch (Exception e) { log.debug("Exportação: seção {} ignorada ({})", name, e.getMessage()); }
  }

  // ───────────── LGPD: apagar minha conta ─────────────

  @PostMapping("/me/delete")
  public ResponseEntity<?> deleteAccount(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (me.getPassword() == null || !passwordEncoder.matches(Objects.toString(b.get("password"), ""), me.getPassword())) return bad("A senha está errada.");
    Long open = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE (customer_id = ? OR professional_id = ?) AND status NOT IN ('DONE','REVIEWED','CANCELLED','BLOCKED')", Long.class, me.getId(), me.getId());
    if (open != null && open > 0) return bad("Você tem " + open + " pedido(s) em andamento. Termine ou cancele antes de apagar a conta.");
    try {
      BigDecimal avail = walletService.available(me.getId());
      if (avail != null && avail.compareTo(BigDecimal.ONE) >= 0) return bad("Você ainda tem R$ " + avail.setScale(2, java.math.RoundingMode.DOWN).toPlainString().replace('.', ',') + " para sacar. Saque antes de apagar a conta.");
    } catch (Exception ignored) { }
    Long id = me.getId();
    String tag = "excluido-" + id;
    // Pedidos e pagamentos ficam guardados sem os dados pessoais (obrigação fiscal); o resto é apagado.
    jdbc.update("UPDATE user_ SET name = 'Conta excluída', surname = NULL, email = ?, username = ?, phone_number = NULL, birthday = NULL, avatar_url = NULL, " +
        "latitude = NULL, longitude = NULL, full_address = NULL, pix_key = NULL, pix_key_type = NULL, document_url = NULL, doc_front_url = NULL, doc_back_url = NULL, doc_selfie_url = NULL, " +
        "password = ?, active = FALSE, deleted_at = ? WHERE id = ?",
        tag + "@excluido.opus", tag, passwordEncoder.encode(UUID.randomUUID().toString()), now(), id);
    for (String sql : new String[]{
        "DELETE FROM identity_verification_ WHERE user_id = ?",
        "DELETE FROM user_device_ WHERE user_id = ?",
        "DELETE FROM favorite_ WHERE user_id = ? OR professional_id = ?",
        "DELETE FROM follow_ WHERE user_id = ? OR professional_id = ?",
        "DELETE FROM post_video_ WHERE post_id IN (SELECT id FROM post_ WHERE professional_id = ?)",
        "DELETE FROM post_ WHERE professional_id = ?",
        "DELETE FROM support_state_ WHERE user_id = ?",
        "DELETE FROM data_export_token_ WHERE user_id = ?"}) {
      try { int n = sql.chars().filter(c -> c == '?').count() == 2 ? jdbc.update(sql, id, id) : jdbc.update(sql, id); }
      catch (Exception e) { log.debug("Exclusão: {} ({})", sql, e.getMessage()); }
    }
    authenticationService.revokeAll(id);
    security.event(id, "ACCOUNT_DELETED", "A pessoa apagou a conta");
    log.info("LGPD: conta {} apagada a pedido da pessoa", id);
    return ResponseEntity.ok(Map.of("ok", true, "message", "Sua conta foi apagada. Os registros de pagamento ficam guardados sem os seus dados pessoais, como a lei exige."));
  }
}
