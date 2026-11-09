package br.com.personalfighters.controller.auth;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.CodeStore;
import br.com.personalfighters.service.LoginResolver;
import br.com.personalfighters.service.SecurityService;
import br.com.personalfighters.service.impl.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Outras formas de entrar:
 * - POST /auth/phone/login {phone, code} → entra com o código do SMS (sem senha);
 * - POST /auth/google {idToken} → entra com o Google (ou devolve os dados para terminar o cadastro).
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AltLoginController {

  private final CodeStore codes;
  private final LoginResolver resolver;
  private final UserDetailsService userDetailsService;
  private final AuthenticationService authenticationService;
  private final SecurityService securityService;
  private final JdbcTemplate jdbc;

  @Value("${GOOGLE_CLIENT_IDS:}")
  private String googleClientIds;

  @PostMapping("/phone/login")
  public ResponseEntity<?> phoneLogin(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    String phone = PhoneVerificationController.normalize(Objects.toString(body.get("phone"), ""));
    if (phone == null) return PhoneVerificationController.bad("Digite um celular válido com DDD.");
    String err = codes.check("SMS", phone, Objects.toString(body.get("code"), ""));
    if (err != null) return PhoneVerificationController.bad(err);
    String username = resolver.phoneOwner(phone);
    Boolean verified = username == null ? null : jdbc.queryForObject("SELECT phone_verified FROM user_ WHERE username = ?", Boolean.class, username);
    // Só entra por SMS quem confirmou esse celular na própria conta
    if (!Boolean.TRUE.equals(verified)) return PhoneVerificationController.bad("Não achamos uma conta com este celular confirmado. Entre com a senha ou crie uma conta.");
    return issue(username, req);
  }

  @PostMapping("/google")
  public ResponseEntity<?> google(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    List<String> allowed = new ArrayList<>();
    for (String c : googleClientIds.split(",")) if (!c.isBlank()) allowed.add(c.trim());
    if (allowed.isEmpty()) return PhoneVerificationController.bad("Entrar com Google ainda não está ligado.");
    Map<?, ?> info;
    try {
      info = WebClient.create("https://oauth2.googleapis.com").get()
          .uri(u -> u.path("/tokeninfo").queryParam("id_token", Objects.toString(body.get("idToken"), "")).build())
          .retrieve().bodyToMono(Map.class).block();
    } catch (Exception e) {
      return PhoneVerificationController.bad("Não foi possível confirmar sua conta Google. Tente de novo.");
    }
    if (info == null || !allowed.contains(String.valueOf(info.get("aud"))) || !"true".equals(String.valueOf(info.get("email_verified")))) {
      return PhoneVerificationController.bad("Conta Google não confirmada.");
    }
    String sub = String.valueOf(info.get("sub"));
    String email = String.valueOf(info.get("email")).toLowerCase();
    List<String> found = jdbc.queryForList("SELECT username FROM user_ WHERE (google_sub = ? OR LOWER(email) = ?) AND username NOT LIKE 'painel:%' ORDER BY (google_sub = ?) DESC LIMIT 1",
        String.class, sub, email, sub);
    if (!found.isEmpty()) {
      jdbc.update("UPDATE user_ SET google_sub = COALESCE(google_sub, ?), email_verified = TRUE WHERE username = ?", sub, found.get(0));
      return issue(found.get(0), req);
    }
    // Ainda não tem conta: o app abre o cadastro já com nome e e-mail (falta celular, CPF, nascimento e termos)
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("needsSignup", true);
    out.put("email", email);
    out.put("name", Objects.toString(info.get("name"), ""));
    out.put("googleToken", codes.proof("google", email + "|" + sub));
    return ResponseEntity.ok(out);
  }

  private ResponseEntity<?> issue(String username, HttpServletRequest req) {
    User user = (User) userDetailsService.loadUserByUsername(username);
    if (user.getActive() != null && !user.getActive()) return PhoneVerificationController.bad("Conta desativada. Fale com o suporte.");
    try {
      securityService.registerDevice(user, req.getHeader("X-Device-Id"), req.getHeader("X-Device-Name"), req.getRemoteAddr());
    } catch (Exception e) { log.warn("Aparelho não registrado: {}", e.getMessage()); }
    try {
      jdbc.update("INSERT INTO login_history_ (user_id, ip_address, user_agent, success, created_at) VALUES (?, ?, ?, TRUE, NOW())",
          user.getId(), req.getRemoteAddr(), Objects.toString(req.getHeader("User-Agent"), "").substring(0, Math.min(200, Objects.toString(req.getHeader("User-Agent"), "").length())));
    } catch (Exception ignored) { }
    return ResponseEntity.ok(Map.of("token", authenticationService.addAuthentication(user)));
  }
}
