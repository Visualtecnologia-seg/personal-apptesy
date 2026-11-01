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
  private final EmailService emailService;
  private final LoginAttemptService loginAttemptService;
  private final LoginHistoryRepository loginHistoryRepository;

  @PostMapping
  @ApiOperation(value = "Autentica o user e retorna o token")
  public ResponseEntity<?> createAuthenticationToken(
      @RequestBody AuthRequest authenticationRequest,
      HttpServletRequest request,
      HttpServletResponse response) throws Exception {

    String email = authenticationRequest.getUsername();
    // Acesso do painel é separado: essas contas não entram pelo app
    if (email != null && (email.trim().toLowerCase().startsWith(PanelAuthController.PREFIX) || email.trim().toLowerCase().endsWith(PanelAuthController.DOMAIN))) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Credenciais inválidas.", "message", "Credenciais inválidas."));
    }
    String ip    = getClientIp(request);
    String ua    = request.getHeader("User-Agent");

    // ── 1. Verificar bloqueio por tentativas ─────────────────────────────────
    if (loginAttemptService.isBlocked(email)) {
      int seconds = loginAttemptService.getSecondsRemaining(email);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
          "error", "Conta bloqueada temporariamente.",
          "message", "Muitas tentativas falhadas. Tente novamente em " + seconds + " segundo(s).",
          "secondsRemaining", seconds
      ));
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      new SecurityContextLogoutHandler().logout(request, response, auth);
    }

    // ── 2. Tentar autenticar ─────────────────────────────────────────────────
    try {
      authenticate(email, authenticationRequest.getPassword());
    } catch (Exception e) {
      loginAttemptService.loginFailed(email);
      // Guardar histórico de falha (silencioso se user não existe)
      try {
        User failedUser = (User) userDetailsService.loadUserByUsername(email);
        saveHistory(failedUser, ip, ua, false);
      } catch (Exception ignored) {}

      if (loginAttemptService.isBlocked(email)) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
            "error", "Conta bloqueada.",
            "message", "Conta bloqueada por 60 segundos após 5 tentativas falhadas.",
            "secondsRemaining", 60
        ));
      }
      int remaining = 5 - loginAttemptService.getAttemptCount(email);
      throw new Exception("Credenciais inválidas." +
          (remaining > 0 ? " " + remaining + " tentativa(s) restante(s)." : ""));
    }

    // ── 3. Sucesso: limpar tentativas, guardar histórico, devolver token ──────
    loginAttemptService.loginSucceeded(email);
    final User user = (User) userDetailsService.loadUserByUsername(email);
    saveHistory(user, ip, ua, true);
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
    return userService.findAuthUser();
  }

  @PutMapping("/new-password")
  @ApiOperation(value = "Atualiza a senha do user")
  @ResponseStatus(HttpStatus.OK)
  public Object updatePassword(@RequestBody NewPasswordData data, HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    User user = userService.findByUsername(principal.getName());
    String newPassword = new BCryptPasswordEncoder().encode(data.getNewPassword());
    user.setPassword(newPassword);
    return userService.update(user) != null;
  }

  @PostMapping("/forgot-password")
  @ApiOperation(value = "Reseta a senha do user")
  @ResponseStatus(HttpStatus.OK)
  public Object forgotPassword(@RequestBody ForgotPassword forgotPassword) {
    User user = userService.findByUsername(forgotPassword.getUsername());
    if (user != null) {
      String newPassword = PasswordGenerator.GenerateWeakPassword(4);
      String passwordHash = new BCryptPasswordEncoder().encode(newPassword);
      user.setPassword(passwordHash);
      emailService.sendNewPassword(user.getEmail(), newPassword, user.getName());
      userService.update(user);
      return true;
    }
    return false;
  }

  @GetMapping("/check/{email}")
  @PermitAll
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo e-mail.")
  public Boolean checkIfExists(@PathVariable String email) {
    return userService.checkIfExists(email);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

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
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
    return request.getRemoteAddr();
  }
}
