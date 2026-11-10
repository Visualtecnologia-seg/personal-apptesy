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
    int sent = 9;
    if (Boolean.TRUE.equals(body.get("todos"))) sent += moreSamples(to, today);
    return ResponseEntity.ok(Map.of("sent", sent));
  }

  /** Exemplos dos e-mails do profissional e da jornada do cliente. */
  private int moreSamples(String to, LocalDate today) {
    Map<String, Object> c = new HashMap<>(); c.put("name", "Mariana Souza"); c.put("email", to);
    Map<String, Object> p = new HashMap<>(); p.put("name", "Carlos Lima"); p.put("email", to);
    automation.verificationApprovedEmail(p, true);
    automation.verificationRejectedEmail(p, true, "A foto do documento ficou desfocada e não dá para ler o CPF.", Arrays.asList("DOC_FRONT", "SELFIE_DOC"));
    automation.walletCreditEmail(p, new BigDecimal("180.00"), "Pintura de parede para Mariana", new BigDecimal("1040.50"));
    automation.onboardingEmail(0, p, false, true, false, true);
    Map<String, Object> r = new HashMap<>(c);
    r.put("id", UUID.fromString("7f3a9c21-0000-4000-8000-000000000000")); r.put("total_cost", new BigDecimal("126.00")); r.put("service_value", new BigDecimal("120.00"));
    r.put("discount", new BigDecimal("12.60")); r.put("coupon_code", "BEMVINDO-K7Q2M"); r.put("service", "Aula de boxe"); r.put("pro", "Rafael Costa");
    r.put("date", java.sql.Date.valueOf(today.plusDays(2))); r.put("start_time", java.sql.Time.valueOf("18:30:00"));
    automation.receiptEmail(r);
    automation.birthdayEmail(0, c, "NIVER-EXEMP", 15);
    Map<String, Object> np = new HashMap<>(); np.put("id", 0); np.put("name", "Juliana Prado"); np.put("services", "Personal trainer, Alongamento");
    Map<String, Object> nc = new HashMap<>(c); nc.put("category", "Personal");
    automation.newProEmail(0, nc, np);
    List<Map<String, Object>> dem = new ArrayList<>();
    for (Object[] d : new Object[][]{{"Instalação elétrica", 14}, {"Troca de tomada", 9}, {"Chuveiro", 5}}) { Map<String, Object> m = new HashMap<>(); m.put("name", d[0]); m.put("n", d[1]); dem.add(m); }
    automation.idleProEmail(0, p, dem);
    Map<String, Object> rb = new HashMap<>(c); rb.put("pro", "Rafael Costa"); rb.put("service", "Aula de boxe"); rb.put("professional_id", 0);
    automation.rebookEmail(0, rb);
    automation.welcomeCouponEmail(0, c, "BEMVINDO-EXEMP", 10);
    Map<String, Object> rf = new HashMap<>(c); rf.put("friend", "Bruno Alves");
    automation.referralEmail(rf, false, 100);
    automation.referralEmail(rf, true, 100);
    Map<String, Object> oc = new HashMap<>(c); oc.put("service", "Instalação elétrica"); oc.put("pro", "Carlos Lima");
    oc.put("date", java.sql.Date.valueOf(today.plusDays(1))); oc.put("start_time", java.sql.Time.valueOf("09:00:00"));
    List<Map<String, Object>> alts = new ArrayList<>();
    for (Object[] d : new Object[][]{{"André Ribeiro", 4.9, 32, 1.8}, {"Paulo Mendes", 4.7, 11, 3.2}, {"Luiz Fernandes", null, 0, 4.5}}) {
      Map<String, Object> m = new HashMap<>(); m.put("id", 0); m.put("name", d[0]); m.put("rating", d[1]); m.put("done", d[2]); m.put("km", d[3]); alts.add(m);
    }
    automation.proCancelledEmail(oc, alts);
    automation.withdrawEmail(p, new BigDecimal("500.00"), "PIX CPF •••.456.789-••", true);
    automation.withdrawEmail(p, new BigDecimal("500.00"), "Banco do Brasil ••••4521", false);
    List<Map<String, Object>> lines = new ArrayList<>();
    String[] ds = {"Instalação elétrica para Mariana", "Troca de chuveiro para João", "Etapa 1 de Reforma elétrica para Ana", "Tomadas para Pedro", "Instalação de ventilador para Lúcia", "Etapa 2 de Reforma elétrica para Ana"};
    double[] vs = {180, 90, 400, 120, 150, 400};
    LocalDate m0 = today.minusMonths(1).withDayOfMonth(1);
    for (int i = 0; i < ds.length; i++) {
      Map<String, Object> m = new HashMap<>(); m.put("created_at", java.sql.Timestamp.valueOf(m0.plusDays(2 + i * 4).atTime(14, 0))); m.put("description", ds[i]); m.put("amount", BigDecimal.valueOf(vs[i])); lines.add(m);
    }
    automation.statementEmail(p, m0, lines, new BigDecimal("900.00"), new BigDecimal("14820.00"));
    automation.securityChangeEmail(to, c, "Sua senha foi trocada", "A senha da sua conta OPUS foi trocada agora.");
    return 17;
  }
}
