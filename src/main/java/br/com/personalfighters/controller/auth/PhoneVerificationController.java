package br.com.personalfighters.controller.auth;

import br.com.personalfighters.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Confirmação do telefone por SMS no cadastro.
 * 1) POST /auth/phone/send {phone}   → manda um código de 6 números (vale 10 min)
 * 2) POST /auth/phone/verify {phone, code} → devolve um "phoneToken" (vale 30 min) que o cadastro exige.
 * Limites: 1 SMS por minuto e 5 por dia para o mesmo número; 5 tentativas por código.
 */
@RestController
@RequestMapping("/auth/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

  private final JdbcTemplate jdbc;
  private final SmsService sms;
  private static final SecureRandom RND = new SecureRandom();
  private static final BCryptPasswordEncoder BC = new BCryptPasswordEncoder();

  @Value("${jwt.secret}")
  private String secret;

  /** "(11) 98765-4321" → "11987654321" (DDD + número). Null se não for celular brasileiro. */
  public static String normalize(String raw) {
    String d = raw == null ? "" : raw.replaceAll("\\D", "");
    if (d.startsWith("55") && d.length() == 13) d = d.substring(2);
    if (d.length() != 11 || d.charAt(2) != '9' || d.charAt(0) == '0') return null;
    return d;
  }

  @PostMapping("/send")
  public ResponseEntity<?> send(@RequestBody Map<String, Object> body) {
    String phone = normalize(Objects.toString(body.get("phone"), ""));
    if (phone == null) return bad("Digite um celular válido com DDD, ex.: (21) 98765-4321.");
    Integer recent = jdbc.queryForObject("SELECT COUNT(*) FROM phone_code_ WHERE phone = ? AND (created_at > NOW() - interval '1 minute' OR (sends_day = CURRENT_DATE AND sends >= 5))", Integer.class, phone);
    if (recent != null && recent > 0) return bad("Espere um pouco para pedir outro código (máximo de 5 por dia).");
    String code = sms.isTestMode() ? "123456" : String.format("%06d", RND.nextInt(1_000_000));
    jdbc.update("INSERT INTO phone_code_ (phone, code_hash, expires_at, attempts, sends, sends_day, created_at) VALUES (?, ?, ?, 0, 1, CURRENT_DATE, NOW()) " +
            "ON CONFLICT (phone) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, attempts = 0, created_at = NOW(), " +
            "sends = CASE WHEN phone_code_.sends_day = CURRENT_DATE THEN phone_code_.sends + 1 ELSE 1 END, sends_day = CURRENT_DATE",
        phone, BC.encode(code), Timestamp.valueOf(LocalDateTime.now().plusMinutes(10)));
    if (!sms.send("+55" + phone, "OPUS: seu código é " + code + ". Não passe este código para ninguém.")) {
      return bad("Não conseguimos enviar o SMS agora. Tente de novo em instantes.");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sent", true);
    out.put("masked", "(" + phone.substring(0, 2) + ") *****-" + phone.substring(7));
    if (sms.isTestMode()) out.put("testMode", true); // demonstração: o app avisa que o código é 123456
    return ResponseEntity.ok(out);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {
    String phone = normalize(Objects.toString(body.get("phone"), ""));
    String code = Objects.toString(body.get("code"), "").replaceAll("\\D", "");
    if (phone == null || code.length() != 6) return bad("Digite o código de 6 números.");
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT code_hash, expires_at, attempts FROM phone_code_ WHERE phone = ?", phone);
    if (rows.isEmpty()) return bad("Peça um código primeiro.");
    Map<String, Object> r = rows.get(0);
    if (((Timestamp) r.get("expires_at")).toLocalDateTime().isBefore(LocalDateTime.now()) || ((Number) r.get("attempts")).intValue() >= 5) {
      jdbc.update("UPDATE phone_code_ SET code_hash = 'x', expires_at = NOW() - interval '1 minute' WHERE phone = ?", phone);
      return bad("Código vencido. Peça um novo.");
    }
    if (!BC.matches(code, (String) r.get("code_hash"))) {
      jdbc.update("UPDATE phone_code_ SET attempts = attempts + 1 WHERE phone = ?", phone);
      return bad("Código incorreto.");
    }
    jdbc.update("UPDATE phone_code_ SET code_hash = 'x', expires_at = NOW() - interval '1 minute' WHERE phone = ?", phone);
    long exp = System.currentTimeMillis() + 30 * 60_000L;
    return ResponseEntity.ok(Map.of("phoneToken", phone + "." + exp + "." + sign(phone + "." + exp)));
  }

  /** Confere o comprovante do SMS: devolve o telefone confirmado, ou null. */
  public String phoneFromToken(String token) {
    if (token == null) return null;
    String[] p = token.split("\\.");
    if (p.length != 3) return null;
    try {
      if (Long.parseLong(p[1]) < System.currentTimeMillis()) return null;
    } catch (NumberFormatException e) { return null; }
    return java.security.MessageDigest.isEqual(sign(p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8), p[2].getBytes(StandardCharsets.UTF_8)) ? p[0] : null;
  }

  private String sign(String data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(("phone:" + secret).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("message", m)); }
}
