package br.com.personalfighters.controller.auth;

import br.com.personalfighters.controller.PanelAuthController;

import br.com.personalfighters.entity.LoginHistory;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.auth.AuthRequest;
import br.com.personalfighters.model.ForgotPassword;
import br.com.personalfighters.model.NewPasswordData;
import br.com.personalfighters.repository.LoginHistoryRepository;
import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.service.LoginAttemptService;
import br.com.personalfighters.service.SecurityService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.AuthenticationService;
import br.com.personalfighters.utils.PasswordGenerator;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin
@Api(tags = {"Authentication"})
@RequestMapping("/auth")
public class AuthenticationController {

  private final AuthenticationManager authenticationManager;
  private final UserDetailsService userDetailsService;
  private final AuthenticationService authenticationService;
  private final UserService userService;
  private final br.com.personalfighters.util.Redactor redactor;
  private final EmailService emailService;
  private final br.com.personalfighters.service.EmailAutomationService emailAutomation;
  private final LoginAttemptService loginAttemptService;
  private final LoginHistoryRepository loginHistoryRepository;
  private final SecurityService securityService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final br.com.personalfighters.service.LoginResolver loginResolver;

  @PostMapping
  @ApiOperation(value = "Autentica o user e retorna o token")
  public ResponseEntity<?> createAuthenticationToken(
      @RequestBody AuthRequest authenticationRequest,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    String email = loginResolver.resolve(authenticationRequest.getUsername()); // usuário, e-mail, CPF ou telefone
    // Acesso do painel é separado: essas contas não entram pelo app
    if (email != null && (email.trim().toLowerCase().startsWith(PanelAuthController.PREFIX) || email.trim().toLowerCase().endsWith(PanelAuthController.DOMAIN))) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais inválidas.", "message", "Credenciais inválidas."));
    }
    String ip    = getClientIp(request);
    String ua    = request.getHeader("User-Agent");
    // Trava por aparelho/rede: quem erra a senha é que fica bloqueado, não o dono da conta
    String devKey = email + "|" + ip;
    String acct = "acct:" + email;
    boolean knownDevice = knownDevice(email, request.getHeader("X-Device-Id"));

    // ── 1. Verificar bloqueio por tentativas ─────────────────────────────────
    if (loginAttemptService.isBlocked(devKey)) return blocked(loginAttemptService.getSecondsRemaining(devKey));
    if (!knownDevice && loginAttemptService.wideBlocked(acct)) return blocked(loginAttemptService.wideSeconds(acct));

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      new SecurityContextLogoutHandler().logout(request, response, auth);
    }

    // ── 2. Tentar autenticar ─────────────────────────────────────────────────
    try {
      authenticate(email, authenticationRequest.getPassword());
    } catch (Exception e) {
      int level = loginAttemptService.loginFailed(devKey);
      boolean wideNow = loginAttemptService.wideFailed(acct);
      // Guardar histórico de falha (silencioso se user não existe)
      try {
        User failedUser = (User) userDetailsService.loadUserByUsername(email);
        saveHistory(failedUser, ip, ua, false);
        // Muitas senhas erradas: avisa o dono por e-mail (no máximo 1 vez por dia)
        if ((level >= 2 || wideNow) && failedUser.getEmail() != null && loginAttemptService.alertAllowed(email))
          emailService.sendSecurityAlert(failedUser.getEmail(), failedUser.getName(), "Tentativas de entrar na sua conta OPUS",
              "Alguém está errando a sua senha",
              "Bloqueamos por alguns minutos quem estava errando a sua senha. Você continua entrando normalmente pelo seu celular. Se não foi você, troque a sua senha no app (Perfil → Segurança).");
      } catch (Exception ignored) {}

      if (loginAttemptService.isBlocked(devKey)) return blocked(loginAttemptService.getSecondsRemaining(devKey));
      int remaining = 5 - loginAttemptService.getAttemptCount(devKey);
      String msg = "Login ou senha incorretos." + (remaining > 0 && remaining < 5 ? " " + remaining + " tentativa(s) restante(s)." : "");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg, "message", msg));
    }

    // ── 3. Sucesso: limpar tentativas, guardar histórico, devolver token ──────
    loginAttemptService.loginSucceeded(devKey);
    loginAttemptService.wideReset(acct);
    final User user = (User) userDetailsService.loadUserByUsername(email);
    saveHistory(user, ip, ua, true);
    if (user.getActive() != null && !user.getActive())
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Login ou senha incorretos.", "message", "Login ou senha incorretos."));
    try { securityService.registerDevice(user, request.getHeader("X-Device-Id"), request.getHeader("X-Device-Name"), ip); }
    catch (Exception e) { log.warn("Aparelho do login não registrado: {}", e.getMessage()); }
    final String token = authenticationService.addAuthentication(user);
    return ResponseEntity.ok(token);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/dashboard")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário autenticado no painel administrativo.")
  @JsonView(Views.FirstLevel.class)
  public Object authUserDashboard() {
    return userService.findAuthUser();
  }

  @GetMapping("/mobile")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário autenticado nos aplicativos.")
  public Object authUserMobile() {
    var u = userService.findAuthUser();
    return redactor.strip(u, u.getId());
  }

  @PutMapping("/new-password")
  @ApiOperation(value = "Atualiza a senha do user")
  @ResponseStatus(HttpStatus.OK)
  public Object updatePassword(@RequestBody NewPasswordData data, HttpServletRequest request) {
    // Troca de senha exige a senha atual (quem pegar o celular desbloqueado não troca a senha da conta)
    User user = userService.findAuthUser();
    BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    if (data.getPassword() == null || !enc.matches(data.getPassword(), user.getPassword()))
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta.");
    String invalid = br.com.personalfighters.util.PasswordRules.check(data.getNewPassword());
    if (invalid != null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, invalid);
    jdbc.update("UPDATE user_ SET password = ? WHERE id = ?", enc.encode(data.getNewPassword()), user.getId());
    // Senha nova derruba todos os outros acessos (quem tinha roubado o login sai na hora) e a digital salva
    authenticationService.revokeAll(user.getId());
    emailAutomation.passwordChanged(user.getId());
    return Map.of("ok", true, "token", authenticationService.addAuthentication(user)); // este aparelho continua logado
  }

  @PostMapping("/forgot-password")
  @ApiOperation(value = "Envia um código para criar nova senha (a senha atual não muda)")
  public Object forgotPassword(@RequestBody Map<String, Object> body) {
    // Sempre a mesma resposta: não revela se o e-mail/usuário tem conta.
    String login = java.util.Objects.toString(body.get("username"), "").trim();
    String key = "reset:" + login.toLowerCase();
    if (!login.isEmpty() && !loginAttemptService.isBlocked(key)) {
      loginAttemptService.loginFailed(key); // limita pedidos repetidos para a mesma conta
      User user = findQuiet(login);
      // No máximo 1 código a cada 2 minutos e 5 por dia para a mesma conta
      Integer recent = user == null ? 0 : jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_ WHERE user_id = ? AND (created_at > NOW() - interval '2 minutes' OR (sends_day = CURRENT_DATE AND sends >= 5))", Integer.class, user.getId());
      if (user != null && (recent == null || recent == 0) && user.getEmail() != null && !String.valueOf(user.getUsername()).startsWith("painel:")) {
        String code = String.format("%06d", new java.security.SecureRandom().nextInt(1_000_000));
        jdbc.update("INSERT INTO password_reset_ (user_id, code_hash, expires_at, attempts, created_at, sends_day, sends) VALUES (?, ?, ?, 0, NOW(), CURRENT_DATE, 1) " +
                "ON CONFLICT (user_id) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, attempts = 0, created_at = NOW(), " +
                "sends = CASE WHEN password_reset_.sends_day = CURRENT_DATE THEN password_reset_.sends + 1 ELSE 1 END, sends_day = CURRENT_DATE",
            user.getId(), new BCryptPasswordEncoder().encode(code), java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusMinutes(15)));
        try { emailService.sendResetCode(user.getEmail(), user.getName(), code); } catch (Exception e) { log.warn("E-mail de código não enviado: {}", e.getMessage()); }
      }
    }
    return Map.of("ok", true, "message", "Se existir uma conta com esse login, enviamos um código para o e-mail cadastrado.");
  }

  @PostMapping("/reset-password")
  @ApiOperation(value = "Cria a nova senha com o código recebido por e-mail")
  public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> body) {
    String login = java.util.Objects.toString(body.get("username"), "").trim();
    String code = java.util.Objects.toString(body.get("code"), "").replaceAll("\\D", "");
    String pass = java.util.Objects.toString(body.get("password"), "");
    String invalid = br.com.personalfighters.util.PasswordRules.check(pass);
    if (invalid != null) return ResponseEntity.badRequest().body(Map.of("message", invalid));
    User user = findQuiet(login);
    if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Código inválido ou vencido. Peça um novo."));
    // Conta a tentativa ANTES de conferir, num passo só (tentativas em paralelo não passam do limite de 5)
    List<String> r = jdbc.queryForList("UPDATE password_reset_ SET attempts = attempts + 1 WHERE user_id = ? AND attempts < 5 AND expires_at > NOW() RETURNING code_hash",
        String.class, user.getId());
    if (r.isEmpty()) {
      // mantém a linha (conta quantos códigos já saíram hoje), só invalida o código
      jdbc.update("UPDATE password_reset_ SET code_hash = 'x', expires_at = NOW() - interval '1 minute' WHERE user_id = ?", user.getId());
      return ResponseEntity.badRequest().body(Map.of("message", "Código inválido ou vencido. Peça um novo."));
    }
    if (code.length() != 6 || !new BCryptPasswordEncoder().matches(code, r.get(0)))
      return ResponseEntity.badRequest().body(Map.of("message", "Código incorreto. Confira o e-mail."));
    jdbc.update("DELETE FROM password_reset_ WHERE user_id = ?", user.getId());
    jdbc.update("UPDATE user_ SET password = ? WHERE id = ?", new BCryptPasswordEncoder().encode(pass), user.getId());
    authenticationService.revokeAll(user.getId()); // derruba os logins antigos
    emailAutomation.passwordChanged(user.getId());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  /** Procura a conta sem revelar (por erro 404) se ela existe. */
  private User findQuiet(String login) {
    String r = loginResolver.resolve(login);
    if (r != null && r.toLowerCase().startsWith("painel:")) return null;
    for (String l : new String[]{r, login, login.toLowerCase()}) {
      try { User u = userService.findByUsername(l); if (u != null) return u; } catch (Exception ignored) { }
    }
    return null;
  }

  private void authenticate(String username, String password) throws Exception {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(username, password));
    } catch (DisabledException e) {
      throw new Exception("Utilizador desactivado.", e);
    } catch (BadCredentialsException e) {
      throw new Exception("Credenciais inválidas.", e);
    } catch (LockedException e) {
      throw new Exception("Utilizador bloqueado.", e);
    }
  }

  private void saveHistory(User user, String ip, String userAgent, boolean success) {
    try {
      String ua = userAgent != null
          ? userAgent.substring(0, Math.min(userAgent.length(), 500)) : null;
      loginHistoryRepository.save(LoginHistory.builder()
          .user(user).ipAddress(ip).userAgent(ua).success(success).build());
    } catch (Exception e) {
      log.warn("Não foi possível guardar histórico de login: {}", e.getMessage());
    }
  }

  private String getClientIp(HttpServletRequest request) {
    return br.com.personalfighters.config.ProtectionFilter.clientIp(request); // IP real (Cloudflare), não o que o aparelho diz
  }

  private static ResponseEntity<?> blocked(int seconds) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
        "error", "Conta bloqueada.",
        "message", "Muitas senhas erradas. Tente de novo em " + Math.max(1, (seconds + 59) / 60) + " minuto(s).",
        "secondsRemaining", seconds
    ));
  }

  /** Celular que já entrou nesta conta antes (o dono não fica trancado por ataque de fora). */
  private boolean knownDevice(String username, String deviceId) {
    if (username == null || deviceId == null || deviceId.isBlank()) return false;
    String dev = deviceId.trim().length() > 80 ? deviceId.trim().substring(0, 80) : deviceId.trim();
    try {
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_device_ d JOIN user_ u ON u.id = d.user_id WHERE u.username = ? AND d.device_id = ?", Integer.class, username, dev);
      return n != null && n > 0;
    } catch (Exception e) { return false; }
  }
}
