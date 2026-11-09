package br.com.personalfighters.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Códigos de 6 números (SMS ou e-mail) e comprovantes assinados.
 * Regras: 1 código por minuto e 5 por dia para o mesmo destino; vale 10 min; 5 tentativas.
 */
@Service
public class CodeStore {

  private final JdbcTemplate jdbc;
  private final String secret;
  private static final SecureRandom RND = new SecureRandom();
  private static final BCryptPasswordEncoder BC = new BCryptPasswordEncoder();

  public CodeStore(JdbcTemplate jdbc, @Value("${jwt.secret}") String secret) {
    this.jdbc = jdbc;
    this.secret = secret;
  }

  /** Gera e guarda um código novo; devolve o código, ou null se pediu rápido demais. */
  public String issue(String channel, String target, boolean testMode) {
    Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM verify_code_ WHERE channel = ? AND target = ? AND (created_at > NOW() - interval '1 minute' OR (sends_day = CURRENT_DATE AND sends >= 5))",
        Integer.class, channel, target);
    if (recent != null && recent > 0) return null;
    String code = testMode ? "123456" : String.format("%06d", RND.nextInt(1_000_000));
    jdbc.update("INSERT INTO verify_code_ (channel, target, code_hash, expires_at, attempts, sends, sends_day, created_at) VALUES (?, ?, ?, ?, 0, 1, CURRENT_DATE, NOW()) " +
            "ON CONFLICT (channel, target) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, attempts = 0, created_at = NOW(), " +
            "sends = CASE WHEN verify_code_.sends_day = CURRENT_DATE THEN verify_code_.sends + 1 ELSE 1 END, sends_day = CURRENT_DATE",
        channel, target, BC.encode(code), Timestamp.valueOf(LocalDateTime.now().plusMinutes(10)));
    return code;
  }

  /** Confere o código (e o "gasta" se estiver certo). Devolve null se ok, ou a mensagem do problema. */
  public String check(String channel, String target, String code) {
    String c = code == null ? "" : code.replaceAll("\\D", "");
    if (c.length() != 6) return "Digite o código de 6 números.";
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT code_hash, expires_at, attempts FROM verify_code_ WHERE channel = ? AND target = ?", channel, target);
    if (rows.isEmpty()) return "Peça um código primeiro.";
    Map<String, Object> r = rows.get(0);
    if (((Timestamp) r.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now()) || ((Number) r.get("attempts")).intValue() >= 5) {
      burn(channel, target);
      return "Código vencido. Peça um novo.";
    }
    if (!BC.matches(c, (String) r.get("code_hash"))) {
      jdbc.update("UPDATE verify_code_ SET attempts = attempts + 1 WHERE channel = ? AND target = ?", channel, target);
      return "Código incorreto.";
    }
    burn(channel, target);
    return null;
  }

  private void burn(String channel, String target) {
    jdbc.update("UPDATE verify_code_ SET code_hash = 'x', expires_at = NOW() - interval '1 minute' WHERE channel = ? AND target = ?", channel, target);
  }

  /** Comprovante assinado "valor.validade.assinatura" (vale 30 min) para usar no cadastro. */
  public String proof(String kind, String value) {
    String v = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    long exp = System.currentTimeMillis() + 30 * 60_000L;
    return v + "." + exp + "." + sign(kind + ":" + v + "." + exp);
  }

  /** Lê o comprovante: devolve o valor confirmado, ou null se for falso ou vencido. */
  public String fromProof(String kind, String token) {
    if (token == null) return null;
    String[] p = token.split("\\.");
    if (p.length != 3) return null;
    try { if (Long.parseLong(p[1]) < System.currentTimeMillis()) return null; } catch (NumberFormatException e) { return null; }
    if (!java.security.MessageDigest.isEqual(sign(kind + ":" + p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8), p[2].getBytes(StandardCharsets.UTF_8))) return null;
    try { return new String(Base64.getUrlDecoder().decode(p[0]), StandardCharsets.UTF_8); } catch (Exception e) { return null; }
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(("codes:" + secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
