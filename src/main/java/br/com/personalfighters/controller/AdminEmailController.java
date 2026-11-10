package br.com.personalfighters.controller;

import br.com.personalfighters.controller.auth.EmailVerificationController;
import br.com.personalfighters.service.EmailAutomationService;
import br.com.personalfighters.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Administrador: manda exemplos de todos os e-mails para conferir como chegam na caixa de entrada. */
@RestController
@RequestMapping("/admin/email")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminEmailController {

  private final EmailService email;
  private final EmailAutomationService automation;
  private volatile long last;

  @PostMapping("/amostras")
  public ResponseEntity<?> samples(@RequestBody Map<String, Object> body) {
    String to = EmailVerificationController.normalize(Objects.toString(body.get("email"), ""));
    if (to == null) return ResponseEntity.badRequest().body(Map.of("message", "Digite um e-mail válido."));
    if (!email.isConfigured()) return ResponseEntity.badRequest().body(Map.of("message", "O envio de e-mail não está configurado no servidor."));
    if (System.currentTimeMillis() - last < 10 * 60_000L) return ResponseEntity.status(429).body(Map.of("message", "Espere 10 minutos para mandar de novo."));
    last = System.currentTimeMillis();

    LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    email.sendWelcome(to, "Mariana Souza", false);
    email.sendWelcome(to, "Carlos Lima", true);

    Map<String, Object> o = new HashMap<>();
    o.put("id", "exemplo"); o.put("name", "Mariana Souza"); o.put("service", "Aula de boxe"); o.put("pro", "Rafael Costa");
    o.put("date", java.sql.Date.valueOf(today.plusDays(2))); o.put("start_time", java.sql.Time.valueOf("18:30:00"));
    o.put("total_cost", new BigDecimal("120.00")); o.put("email", to);
    automation.abandonedEmail(0, o);

    Map<String, Object> u = new HashMap<>();
    u.put("name", "Mariana Souza"); u.put("email", to);
    automation.winBackEmail(0, u);

    Map<String, Object> p = new HashMap<>();
    p.put("name", "Carlos Lima"); p.put("email", to);
    automation.weeklyEmail(0, p, today.with(DayOfWeek.MONDAY), 7, new BigDecimal("1340.00"), new BigDecimal("1100.00"), "4,9", 5, new BigDecimal("860.50"), 4);

    email.sendEmailCode(to, "482913");
    email.sendPayoutCode(to, "Carlos Lima", "591044");
    email.sendSecurityAlert(to, "Mariana Souza", "Novo acesso à sua conta OPUS", "Novo acesso à sua conta",
        "Novo acesso à sua conta OPUS em Galaxy A54. Foi você? Se não foi, abra o app → Perfil → Segurança → Não fui eu.");
    email.sendOrderConfirmed(to, "Mariana Souza", today.plusDays(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")), "18:30");
    return ResponseEntity.ok(Map.of("sent", 9));
  }
}
