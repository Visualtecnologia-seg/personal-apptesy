package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.LoginAttemptService;
import br.com.personalfighters.service.TotpService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acesso do painel administrativo, separado das contas do app.
 * As contas do painel ficam em user_ com username "painel:<login>" e só têm o papel ADMIN.
 * Elas não entram pelo app, e contas do app não têm poder de administrador.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PanelAuthController implements ApplicationRunner {

  public static final String PREFIX = "painel:";
  public static final String DOMAIN = "@painel.opus";

  private final UserRepository userRepository;
  private final UserService userService;
  private final AuthenticationService authenticationService;
  private final LoginAttemptService loginAttemptService;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbc;
  private final TotpService totp;

  /** Login em andamento: senha certa, esperando o código de 6 dígitos (vale 5 minutos, até 5 tentativas). */
  private static final class Ticket { final Long userId; final String login; final long exp; int tries; Ticket(Long u, String l) { userId = u; login = l; exp = System.currentTimeMillis() + 5 * 60_000; } }
  private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

  @Value("${ADMIN_SETUP_CODE:}")
  private String envSetupCode;
  private String setupCode;

  /** Ao iniciar: sem nenhum acesso do painel, gera um código de primeiro acesso (aparece no log do Render). */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (panelCount() == 0) {
      setupCode = envSetupCode != null && !envSetupCode.isBlank() ? envSetupCode.trim() : String.format("%06d", new SecureRandom().nextInt(1_000_000));
      log.warn("==== PAINEL OPUS: código de primeiro acesso = {} ====", setupCode);
    } else {
      // Contas do app não ficam com papel de administrador
      int n = jdbc.update("DELETE FROM user_role WHERE role = 'ADMIN' AND user_id IN (SELECT id FROM user_ WHERE username NOT LIKE 'painel:%')");
      if (n > 0) log.info("Painel: papel de administrador retirado de {} conta(s) do app", n);
    }
  }

  private long panelCount() {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE username LIKE 'painel:%' AND COALESCE(active, TRUE) = TRUE", Long.class);
    return n == null ? 0 : n;
  }

  private static String clean(Object o) { return Objects.toString(o, "").trim().toLowerCase(); }
  private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("error", m, "message", m)); }

  @GetMapping("/panel-auth/status")
  public Map<String, Object> status() { return Map.of("needsSetup", panelCount() == 0); }

  /** Cria o primeiro acesso do painel com o código do log. */
  @PostMapping("/panel-auth/setup")
  @Transactional
  public ResponseEntity<?> setup(@RequestBody Map<String, Object> b) {
    if (panelCount() > 0) return bad("O painel já tem acesso criado. Entre com seu login.");
    if (setupCode == null || !setupCode.equals(Objects.toString(b.get("code"), "").trim())) return bad("Código de primeiro acesso incorreto.");
    ResponseEntity<?> r = create(clean(b.get("login")), Objects.toString(b.get("password"), ""));
    if (r.getStatusCode().is2xxSuccessful()) {
      setupCode = null;
      jdbc.update("DELETE FROM user_role WHERE role = 'ADMIN' AND user_id IN (SELECT id FROM user_ WHERE username NOT LIKE 'painel:%')");
    }
    return r;
  }

  @PostMapping("/panel-auth/login")
  public ResponseEntity<?> login(@RequestBody Map<String, Object> b) {
    String login = clean(b.get("login"));
    String key = PREFIX + login;
    if (loginAttemptService.isBlocked(key)) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Muitas tentativas. Tente de novo em " + loginAttemptService.getSecondsRemaining(key) + " segundos."));
    }
    User u = login.isEmpty() ? null : userRepository.findByUsername(key);
    if (u == null || !u.isEnabled() || u.getPassword() == null || !passwordEncoder.matches(Objects.toString(b.get("password"), ""), u.getPassword())) {
      loginAttemptService.loginFailed(key);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Login ou senha incorretos."));
    }
    if (twoFactorOn(u.getId())) {
      tickets.values().removeIf(t -> t.exp < System.currentTimeMillis());
      String ticket = UUID.randomUUID().toString();
      tickets.put(ticket, new Ticket(u.getId(), login));
      return ResponseEntity.ok(Map.of("need2fa", true, "ticket", ticket));
    }
    loginAttemptService.loginSucceeded(key);
    return ResponseEntity.ok(Map.of("token", authenticationService.addAuthentication(u), "login", login, "twoFactor", false));
  }

  /** Segunda etapa: código do aplicativo autenticador (ou um código de emergência). */
  @PostMapping("/panel-auth/2fa")
  @Transactional
  public ResponseEntity<?> secondStep(@RequestBody Map<String, Object> b) {
    Ticket t = tickets.get(Objects.toString(b.get("ticket"), ""));
    if (t == null || t.exp < System.currentTimeMillis()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "O tempo acabou. Entre com login e senha de novo."));
    String key = PREFIX + t.login;
    if (loginAttemptService.isBlocked(key)) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "Muitas tentativas. Tente de novo em " + loginAttemptService.getSecondsRemaining(key) + " segundos."));
    String code = Objects.toString(b.get("code"), "").trim();
    boolean ok = checkCode(t.userId, code, true);
    if (!ok) {
      loginAttemptService.loginFailed(key);
      if (++t.tries >= 5) tickets.remove(Objects.toString(b.get("ticket"), ""));
      log.warn("Painel: código de 2 etapas errado para {}", t.login);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Código incorreto. Confira no aplicativo autenticador."));
    }
    tickets.remove(Objects.toString(b.get("ticket"), ""));
    loginAttemptService.loginSucceeded(key);
    User u = userRepository.findById(t.userId).orElseThrow();
    return ResponseEntity.ok(Map.of("token", authenticationService.addAuthentication(u), "login", t.login, "twoFactor", true));
  }

  // ───────────── Verificação em duas etapas (do próprio acesso) ─────────────

  private boolean twoFactorOn(Long userId) {
    Boolean on = jdbc.query("SELECT enabled FROM panel_2fa_ WHERE user_id = ?", rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, userId);
    return Boolean.TRUE.equals(on);
  }

  /** Confere o código de 6 dígitos ou, se permitido, um código de emergência (que deixa de valer). */
  private boolean checkCode(Long userId, String code, boolean allowRecovery) {
    Map<String, Object> r = jdbc.queryForList("SELECT secret, last_step FROM panel_2fa_ WHERE user_id = ? AND enabled = TRUE", userId).stream().findFirst().orElse(null);
    if (r == null) return false;
    String digits = code.replaceAll("\\D", "");
    if (digits.length() == 6) {
      long step = totp.verify(totp.decrypt((String) r.get("secret")), digits, ((Number) r.get("last_step")).longValue());
      if (step < 0) return false;
      jdbc.update("UPDATE panel_2fa_ SET last_step = ? WHERE user_id = ?", step, userId);
      return true;
    }
    if (allowRecovery && digits.length() == 8) {
      int n = jdbc.update("UPDATE panel_2fa_recovery_ SET used_at = ? WHERE user_id = ? AND code_hash = ? AND used_at IS NULL", Timestamp.valueOf(LocalDateTime.now()), userId, totp.hash(digits));
      if (n > 0) log.warn("Painel: código de emergência usado pelo acesso {}", userId);
      return n > 0;
    }
    return false;
  }

  @GetMapping("/admin/me/2fa")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> myTwoFactor() {
    User me = userService.findAuthUser();
    Long left = jdbc.queryForObject("SELECT COUNT(*) FROM panel_2fa_recovery_ WHERE user_id = ? AND used_at IS NULL", Long.class, me.getId());
    return Map.of("enabled", twoFactorOn(me.getId()), "recoveryLeft", left == null ? 0 : left);
  }

  /** Passo 1 da ativação: gera o segredo e o QR code para o aplicativo autenticador. */
  @PostMapping("/admin/me/2fa/setup")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> setupTwoFactor() {
    User me = userService.findAuthUser();
    if (twoFactorOn(me.getId())) return bad("A verificação em duas etapas já está ligada.");
    String secret = totp.newSecret();
    jdbc.update("INSERT INTO panel_2fa_ (user_id, pending_secret, enabled, updated_at) VALUES (?, ?, FALSE, ?) ON CONFLICT (user_id) DO UPDATE SET pending_secret = EXCLUDED.pending_secret, updated_at = EXCLUDED.updated_at",
        me.getId(), totp.encrypt(secret), Timestamp.valueOf(LocalDateTime.now()));
    String account = me.getUsername().startsWith(PREFIX) ? me.getUsername().substring(PREFIX.length()) : me.getUsername();
    return ResponseEntity.ok(Map.of("uri", totp.otpauthUri(account, secret), "secret", secret.replaceAll("(.{4})", "$1 ").trim()));
  }

  /** Passo 2: confere o primeiro código e liga. Devolve os códigos de emergência (mostrados uma vez só). */
  @PostMapping("/admin/me/2fa/enable")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> enableTwoFactor(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Map<String, Object> r = jdbc.queryForList("SELECT pending_secret FROM panel_2fa_ WHERE user_id = ?", me.getId()).stream().findFirst().orElse(null);
    if (r == null || r.get("pending_secret") == null) return bad("Comece pelo QR code.");
    String secret = totp.decrypt((String) r.get("pending_secret"));
    long step = totp.verify(secret, Objects.toString(b.get("code"), ""), 0);
    if (step < 0) return bad("Código incorreto. Confira se o horário do celular está automático e tente o código novo.");
    jdbc.update("UPDATE panel_2fa_ SET secret = pending_secret, pending_secret = NULL, enabled = TRUE, last_step = ?, enabled_at = ?, updated_at = ? WHERE user_id = ?",
        step, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), me.getId());
    jdbc.update("DELETE FROM panel_2fa_recovery_ WHERE user_id = ?", me.getId());
    List<String> codes = totp.recoveryCodes(8);
    for (String c : codes) jdbc.update("INSERT INTO panel_2fa_recovery_ (user_id, code_hash) VALUES (?, ?)", me.getId(), totp.hash(c));
    log.info("Painel: verificação em duas etapas ligada para o acesso {}", me.getId());
    return ResponseEntity.ok(Map.of("enabled", true, "recoveryCodes", codes));
  }

  /** Gera novos códigos de emergência (os antigos deixam de valer). Pede o código atual. */
  @PostMapping("/admin/me/2fa/recovery")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> newRecovery(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (!checkCode(me.getId(), Objects.toString(b.get("code"), ""), false)) return bad("Código incorreto.");
    jdbc.update("DELETE FROM panel_2fa_recovery_ WHERE user_id = ?", me.getId());
    List<String> codes = totp.recoveryCodes(8);
    for (String c : codes) jdbc.update("INSERT INTO panel_2fa_recovery_ (user_id, code_hash) VALUES (?, ?)", me.getId(), totp.hash(c));
    return ResponseEntity.ok(Map.of("recoveryCodes", codes));
  }

  /** Desliga (pede senha + código atual). */
  @PostMapping("/admin/me/2fa/disable")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> disableTwoFactor(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (!passwordEncoder.matches(Objects.toString(b.get("password"), ""), me.getPassword())) return bad("A senha está errada.");
    if (!checkCode(me.getId(), Objects.toString(b.get("code"), ""), true)) return bad("Código incorreto.");
    jdbc.update("DELETE FROM panel_2fa_ WHERE user_id = ?", me.getId());
    jdbc.update("DELETE FROM panel_2fa_recovery_ WHERE user_id = ?", me.getId());
    log.warn("Painel: verificação em duas etapas DESLIGADA pelo acesso {}", me.getId());
    return ResponseEntity.ok(Map.of("enabled", false));
  }

  /** Para quem perdeu o celular: outro administrador (com 2 etapas ligada) zera a verificação dessa pessoa. */
  @PostMapping("/admin/team/{id}/2fa-reset")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> resetTeamTwoFactor(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (me.getId().equals(id)) return bad("Para o seu próprio acesso, use um código de emergência.");
    if (!checkCode(me.getId(), Objects.toString(b.get("code"), ""), false)) return bad("Para fazer isso, ligue a sua verificação em duas etapas e digite o seu código.");
    jdbc.update("DELETE FROM panel_2fa_ WHERE user_id = ?", id);
    jdbc.update("DELETE FROM panel_2fa_recovery_ WHERE user_id = ?", id);
    log.warn("Painel: acesso {} zerou a verificação em duas etapas do acesso {}", me.getId(), id);
    return ResponseEntity.ok(team());
  }

  // ───────────── Equipe (só administradores do painel) ─────────────

  @GetMapping("/admin/team")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> team() {
    return jdbc.queryForList("SELECT u.id, SUBSTRING(u.username FROM 8) AS login, COALESCE(u.active, TRUE) AS active, COALESCE(f.enabled, FALSE) AS \"twoFactor\" " +
        "FROM user_ u LEFT JOIN panel_2fa_ f ON f.user_id = u.id WHERE u.username LIKE 'painel:%' ORDER BY u.username");
  }

  @PostMapping("/admin/team")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> add(@RequestBody Map<String, Object> b) {
    ResponseEntity<?> r = create(clean(b.get("login")), Objects.toString(b.get("password"), ""));
    return r.getStatusCode().is2xxSuccessful() ? ResponseEntity.ok(team()) : r;
  }

  @DeleteMapping("/admin/team/{id}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> remove(@PathVariable Long id) {
    User me = userService.findAuthUser();
    if (me.getId().equals(id)) return bad("Você não pode tirar o seu próprio acesso.");
    User u = userRepository.findById(id).orElse(null);
    if (u == null || !u.getUsername().startsWith(PREFIX)) return bad("Acesso não encontrado.");
    u.setActive(false);
    userRepository.save(u);
    return ResponseEntity.ok(team());
  }

  @PostMapping("/admin/me/password")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @Transactional
  public ResponseEntity<?> changePassword(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (!passwordEncoder.matches(Objects.toString(b.get("current"), ""), me.getPassword())) return bad("A senha atual está errada.");
    String np = Objects.toString(b.get("password"), "");
    if (np.length() < 8) return bad("A nova senha precisa ter pelo menos 8 caracteres.");
    me.setPassword(passwordEncoder.encode(np));
    userRepository.save(me);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  private ResponseEntity<?> create(String login, String password) {
    if (!login.matches("[a-z0-9._-]{3,30}")) return bad("O login precisa ter de 3 a 30 letras, números, ponto, traço ou sublinhado, sem espaço e sem @.");
    if (password.length() < 8) return bad("A senha precisa ter pelo menos 8 caracteres.");
    if (userRepository.findByUsername(PREFIX + login) != null) return bad("Esse login já existe.");
    User u = User.builder().username(PREFIX + login).email(login + DOMAIN).name("Administrador").cpf("00000000000")
        .password(passwordEncoder.encode(password)).role(new ArrayList<>(List.of(Role.ADMIN))).active(true).build();
    userRepository.save(u);
    return ResponseEntity.ok(Map.of("ok", true));
  }
}
