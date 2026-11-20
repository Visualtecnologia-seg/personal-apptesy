package br.com.personalfighters.controller.auth;

import br.com.personalfighters.config.ProtectionFilter;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.SecurityService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Entrar com a digital sem guardar a senha no celular.
 * POST /auth/bio/enable {password} (logado) → devolve uma chave só para a digital deste celular;
 * POST /auth/bio/login {token} → entra com essa chave;
 * POST /auth/bio/disable (logado) → apaga a chave deste celular.
 * A chave vale 180 dias e morre ao trocar a senha, no "não fui eu" e no "sair de todos os aparelhos".
 */
@Slf4j
@RestController
@RequestMapping("/auth/bio")
@RequiredArgsConstructor
public class BioLoginController {

  private static final SecureRandom RND = new SecureRandom();
  private static final int MAX_PER_USER = 5;

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserDetailsService userDetailsService;
  private final AuthenticationService authenticationService;
  private final SecurityService securityService;

  @PostMapping("/enable")
  public ResponseEntity<?> enable(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    User me = userService.findAuthUser();
    // Pede a senha de novo: um login roubado não vira chave permanente
    String pass = Objects.toString(body.get("password"), "");
    if (me.getPassword() == null || !new BCryptPasswordEncoder().matches(pass, me.getPassword()))
      return ResponseEntity.badRequest().body(Map.of("message", "Senha incorreta."));
    String dev = device(req);
    byte[] raw = new byte[32];
    RND.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    if (dev != null) jdbc.update("DELETE FROM bio_token_ WHERE user_id = ? AND device_id = ?", me.getId(), dev);
    Long tv = jdbc.queryForObject("SELECT COALESCE(token_version, 0) FROM user_ WHERE id = ?", Long.class, me.getId());
    jdbc.update("INSERT INTO bio_token_ (user_id, token_hash, device_id, token_version, created_at) VALUES (?, ?, ?, ?, NOW())",
        me.getId(), sha256(token), dev, tv == null ? 0 : tv);
    // no máximo 5 celulares com digital por conta (apaga os mais antigos)
    jdbc.update("DELETE FROM bio_token_ WHERE user_id = ? AND id NOT IN (SELECT id FROM bio_token_ WHERE user_id = ? ORDER BY created_at DESC LIMIT ?)",
        me.getId(), me.getId(), MAX_PER_USER);
    return ResponseEntity.ok(Map.of("bioToken", token));
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    String token = Objects.toString(body.get("token"), "");
    if (token.length() < 20 || token.length() > 100) return denied();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT b.id, b.user_id, b.token_version, u.username, COALESCE(u.token_version, 0) AS cur FROM bio_token_ b JOIN user_ u ON u.id = b.user_id " +
            "WHERE b.token_hash = ? AND b.created_at > NOW() - interval '180 days'", sha256(token));
    if (rows.isEmpty()) return denied();
    Map<String, Object> r = rows.get(0);
    String username = (String) r.get("username");
    if (username == null || username.startsWith("painel:")) return denied();
    if (((Number) r.get("token_version")).longValue() != ((Number) r.get("cur")).longValue()) {
      jdbc.update("DELETE FROM bio_token_ WHERE id = ?", r.get("id"));
      return denied();
    }
    User user = (User) userDetailsService.loadUserByUsername(username);
    if (!user.isEnabled() || (user.getActive() != null && !user.getActive())) return denied();
    jdbc.update("UPDATE bio_token_ SET last_used = NOW() WHERE id = ?", r.get("id"));
    String ip = ProtectionFilter.clientIp(req);
    try { securityService.registerDevice(user, req.getHeader("X-Device-Id"), req.getHeader("X-Device-Name"), ip); }
    catch (Exception e) { log.warn("Aparelho não registrado: {}", e.getMessage()); }
    try {
      String ua = Objects.toString(req.getHeader("User-Agent"), "");
      jdbc.update("INSERT INTO login_history_ (user_id, ip_address, user_agent, success, created_at) VALUES (?, ?, ?, TRUE, NOW())",
          user.getId(), ip, ua.substring(0, Math.min(200, ua.length())));
    } catch (Exception ignored) { }
    return ResponseEntity.ok(Map.of("token", authenticationService.addAuthentication(user)));
  }

  @PostMapping("/disable")
  public Map<String, Object> disable(HttpServletRequest req) {
    User me = userService.findAuthUser();
    String dev = device(req);
    if (dev != null) jdbc.update("DELETE FROM bio_token_ WHERE user_id = ? AND device_id = ?", me.getId(), dev);
    return Map.of("ok", true);
  }

  private static ResponseEntity<?> denied() {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "A digital deste celular expirou. Entre com a senha."));
  }

  private static String device(HttpServletRequest req) {
    String d = req.getHeader("X-Device-Id");
    if (d == null || d.isBlank()) return null;
    d = d.trim();
    return d.length() > 80 ? d.substring(0, 80) : d;
  }

  static String sha256(String s) {
    try {
      StringBuilder b = new StringBuilder();
      for (byte x : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))) b.append(String.format("%02x", x));
      return b.toString();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
}
