package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.LoginAttemptService;
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
import java.util.*;

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
    loginAttemptService.loginSucceeded(key);
    return ResponseEntity.ok(Map.of("token", authenticationService.addAuthentication(u), "login", login));
  }

  // ───────────── Equipe (só administradores do painel) ─────────────

  @GetMapping("/admin/team")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> team() {
    return jdbc.queryForList("SELECT id, SUBSTRING(username FROM 8) AS login, COALESCE(active, TRUE) AS active FROM user_ WHERE username LIKE 'painel:%' ORDER BY username");
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
