package br.com.personalfighters.service;

import br.com.personalfighters.util.MailTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * E-mails automáticos:
 *  - "Faltou terminar o pedido": pedido aguardando pagamento há mais de 2 h (uma vez por pedido).
 *  - "Sentimos sua falta": cliente sem entrar nem pedir nada há 30 dias (no máximo a cada 60 dias).
 *  - Resumo semanal do profissional (segunda de manhã, junto com o aviso no app).
 * Todos respeitam quem pediu para não receber (user_.email_marketing) e levam o link para sair.
 */
@Slf4j
@Service
public class EmailAutomationService {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private static final Locale BR = new Locale("pt", "BR");
  /** Plano gratuito do provedor: 300 por dia. Os lembretes usam no máximo esta parte. */
  private static final int WINBACK_PER_DAY = 80, ABANDONED_PER_RUN = 40;

  private final JdbcTemplate jdbc;
  private final EmailService email;
  private final byte[] key;
  private SettingsService settings;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setSettings(SettingsService settings) { this.settings = settings; }

  private int setting(String k, int def) {
    try { return settings == null ? def : settings.getInt(k); } catch (Exception e) { return def; }
  }

  public EmailAutomationService(JdbcTemplate jdbc, EmailService email, @Value("${jwt.secret}") String secret) {
    this.jdbc = jdbc;
    this.email = email;
    this.key = ("email-sair:" + secret).getBytes(StandardCharsets.UTF_8);
  }

  // ---------- link para parar de receber ----------

  public String token(long userId) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] h = mac.doFinal(("u" + userId).getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0, 24);
    } catch (Exception e) { throw new IllegalStateException(e); }
  }

  public boolean tokenOk(long userId, String t) {
    return t != null && MessageDigest.isEqual(token(userId).getBytes(StandardCharsets.UTF_8), t.getBytes(StandardCharsets.UTF_8));
  }

  public String unsubscribeUrl(long userId) {
    return MailTemplate.base() + "/email/sair?u=" + userId + "&t=" + token(userId);
  }

  public void setMarketing(long userId, boolean on) {
    jdbc.update("UPDATE user_ SET email_marketing = ? WHERE id = ?", on, userId);
  }

  /** Marca como enviado; falso se já tinha sido enviado antes (não manda de novo). */
  private boolean mark(long userId, String kind, String ref) {
    return jdbc.update("INSERT INTO email_log_ (user_id, kind, ref) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", userId, kind, ref) > 0;
  }

  private static String money(Object v) {
    double d = v == null ? 0 : ((Number) v).doubleValue();
    return String.format(BR, "R$ %,.2f", d);
  }

  private static String first(Object name) {
    String n = Objects.toString(name, "").trim();
    return n.isEmpty() ? "" : n.split("\\s+")[0];
  }

  private static final String ACTIVE = "u.deleted_at IS NULL AND COALESCE(u.blocked,false) = false AND COALESCE(u.email_marketing,true) = true " +
      "AND u.email LIKE '%@%' AND u.panel_matricula IS NULL";

  // ---------- 1) faltou terminar o pedido ----------

  @Scheduled(cron = "0 15 * * * *", zone = "America/Sao_Paulo")
  public void abandonedPayments() {
    if (!email.isConfigured()) return;
    try {
      LocalDateTime now = LocalDateTime.now(Z);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT o.id, o.customer_id, o.date, o.start_time, o.total_cost, o.service_value, u.name, u.email, p.name AS service, pr.name AS pro " +
              "FROM order_ o JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id LEFT JOIN user_ pr ON pr.id = o.professional_id " +
              "WHERE o.status = 'AWAITING_PAYMENT' AND COALESCE(o.pix_paid,false) = false AND o.created_at < ? AND o.created_at > ? AND " + ACTIVE +
              " AND (o.date IS NULL OR o.date >= ?) " +
              "AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = o.customer_id AND l.kind = 'ABANDONED' AND l.ref = CAST(o.id AS varchar)) " +
              "ORDER BY o.created_at LIMIT " + ABANDONED_PER_RUN,
          Timestamp.valueOf(now.minusHours(2)), Timestamp.valueOf(now.minusHours(48)), java.sql.Date.valueOf(now.toLocalDate()));
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("customer_id")).longValue();
        String oid = r.get("id").toString();
        if (!mark(uid, "ABANDONED", oid)) continue;
        abandonedEmail(uid, r);
      }
    } catch (Exception e) { log.warn("E-mail de pedido sem pagamento: {}", e.getMessage()); }
  }

  public void abandonedEmail(long uid, Map<String, Object> r) {
    String service = Objects.toString(r.get("service"), "seu serviço");
    String pro = first(r.get("pro"));
    Object value = r.get("total_cost") != null ? r.get("total_cost") : r.get("service_value");
    String when = "";
    if (r.get("date") != null) {
      when = ((java.sql.Date) r.get("date")).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"));
      if (r.get("start_time") != null) when += " às " + r.get("start_time").toString().substring(0, 5);
    }
    MailTemplate t = MailTemplate.client()
        .preheader("Seu pedido de " + service + " está quase pronto. Falta só o pagamento.")
        .hero("pagamento.jpg").kicker("Falta pouco").title("Faltou terminar o seu pedido")
        .p("Olá" + (first(r.get("name")).isEmpty() ? "" : ", " + first(r.get("name"))) + "! Seu pedido está separado, mas o pagamento ainda não foi feito. " +
            "Assim que você pagar, o horário" + (pro.isEmpty() ? "" : " com " + pro) + " fica confirmado.");
    List<String[]> det = MailTemplate.rows("Serviço", service);
    if (!pro.isEmpty()) det.add(new String[]{"Profissional", pro});
    if (!when.isEmpty()) det.add(new String[]{"Quando", when});
    if (value != null) det.add(new String[]{"Valor", money(value)});
    t.details(det)
        .button("Terminar o pagamento", MailTemplate.openApp(false, "pagar/" + r.get("id")))
        .alert("Pagamento protegido: o dinheiro fica guardado e o profissional só recebe depois que o serviço termina.", false)
        .small("Mudou de ideia? Não precisa fazer nada. O pedido é cancelado sozinho.")
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), "Seu pedido de " + service + " está esperando", t);
  }

  // ---------- 2) sentimos sua falta ----------

  @Scheduled(cron = "0 0 10 * * *", zone = "America/Sao_Paulo")
  public void winBack() {
    if (!email.isConfigured()) return;
    try {
      Timestamp d30 = Timestamp.valueOf(LocalDateTime.now(Z).minusDays(30));
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT u.id, u.name, u.email FROM user_ u WHERE " + ACTIVE +
              " AND COALESCE(u.created_at_, '2000-01-01') < ? " +
              " AND COALESCE(u.verification_status,'NONE') IN ('NONE','') " + // profissionais têm outro e-mail (resumo semanal)
              " AND NOT EXISTS (SELECT 1 FROM order_ o WHERE o.professional_id = u.id)" +
              " AND NOT EXISTS (SELECT 1 FROM login_history_ h WHERE h.user_id = u.id AND h.success = true AND h.created_at > ?)" +
              " AND NOT EXISTS (SELECT 1 FROM order_ o WHERE o.customer_id = u.id AND o.created_at > ?)" +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = u.id AND l.kind = 'WINBACK' AND l.sent_at > ?)" +
              " ORDER BY u.id LIMIT " + WINBACK_PER_DAY,
          d30, d30, d30, Timestamp.valueOf(LocalDateTime.now(Z).minusDays(60)));
      String ref = LocalDate.now(Z).toString();
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        if (!mark(uid, "WINBACK", ref)) continue;
        winBackEmail(uid, r);
      }
    } catch (Exception e) { log.warn("E-mail sentimos sua falta: {}", e.getMessage()); }
  }

  public void winBackEmail(long uid, Map<String, Object> r) {
    String name = first(r.get("name"));
    // Números só aparecem quando já impressionam (nada de "+0")
    List<String[]> stats = new java.util.ArrayList<>();
    long pros = count("SELECT COUNT(*) FROM user_ WHERE verification_status = 'APPROVED' AND deleted_at IS NULL");
    long done = count("SELECT COUNT(*) FROM order_ WHERE status IN ('DONE','REVIEWED')");
    String avg = rating(null);
    if (pros >= 10) stats.add(new String[]{"profissionais verificados", "+" + pros});
    if (done >= 20) stats.add(new String[]{"serviços feitos", "+" + done});
    if (!"—".equals(avg)) stats.add(new String[]{"nota média dos profissionais", avg});
    MailTemplate t = MailTemplate.client()
        .preheader("Faz um tempo que você não aparece. Veja o que tem de novo perto de você.")
        .hero("alongamento.jpg").kicker("Sentimos sua falta").title(name.isEmpty() ? "Faz tempo que você não aparece" : name + ", faz tempo que você não aparece")
        .p("Enquanto isso, o OPUS não parou: tem profissionais novos perto de você, de aulas a serviços para a casa.");
    if (stats.size() >= 2) t.stats(stats);
    t
        .checklist(java.util.Arrays.asList(
            "**Aulas**: personal, luta, alongamento e funcional.",
            "**Casa**: eletricista, encanador, pintura e jardinagem.",
            "**Seguro**: você paga pelo app e o profissional só recebe no fim."))
        .button("Ver profissionais perto de mim", MailTemplate.openApp(false, null))
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), name.isEmpty() ? "Sentimos sua falta no OPUS" : name + ", sentimos sua falta", t);
  }

  private long count(String sql) {
    try { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0 : v; } catch (Exception e) { return 0; }
  }

  private String rating(Long proId) {
    try {
      Double v = proId == null
          ? jdbc.queryForObject("SELECT AVG(rating) FROM review_ WHERE COALESCE(type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO'", Double.class)
          : jdbc.queryForObject("SELECT AVG(rating) FROM review_ WHERE professional_id = ? AND COALESCE(type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO'", Double.class, proId);
      return v == null ? "—" : String.format(BR, "%.1f", v);
    } catch (Exception e) { return "—"; }
  }

  // ---------- 3) resumo semanal do profissional ----------

  /** Chamado pelo resumo semanal (segunda, 8 h), uma vez por profissional por semana. */
  public void weeklyPro(long proId, LocalDate monday, long services, BigDecimal earned) {
    if (!email.isConfigured()) return;
    try {
      List<Map<String, Object>> us = jdbc.queryForList("SELECT u.id, u.name, u.email FROM user_ u WHERE u.id = ? AND " + ACTIVE, proId);
      if (us.isEmpty()) return;
      if (!mark(proId, "WEEKLY", monday.toString())) return;
      Timestamp from = Timestamp.valueOf(monday.minusDays(7).atStartOfDay()), to = Timestamp.valueOf(monday.atStartOfDay());
      Long reviews = jdbc.queryForObject("SELECT COUNT(*) FROM review_ WHERE professional_id = ? AND date >= ? AND date < ? AND COALESCE(type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO'",
          Long.class, proId, from, to);
      BigDecimal in = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND status = 'DONE'", BigDecimal.class, proId);
      BigDecimal out = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'WITHDRAW' AND status <> 'FAILED'", BigDecimal.class, proId);
      Long next = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('CONFIRMED','AWAITING_PAYMENT','IN_PROGRESS') AND date >= ? AND date < ?",
          Long.class, proId, java.sql.Date.valueOf(monday), java.sql.Date.valueOf(monday.plusDays(7)));
      BigDecimal prev = jdbc.queryForObject("SELECT COALESCE(SUM(service_value),0) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED') AND done_at >= ? AND done_at < ?",
          BigDecimal.class, proId, Timestamp.valueOf(monday.minusDays(14).atStartOfDay()), from);
      weeklyEmail(proId, us.get(0), monday, services, earned, prev, rating(proId), reviews == null ? 0 : reviews,
          (in == null ? BigDecimal.ZERO : in).subtract(out == null ? BigDecimal.ZERO : out), next == null ? 0 : next);
    } catch (Exception e) { log.warn("E-mail resumo semanal {}: {}", proId, e.getMessage()); }
  }

  public void weeklyEmail(long proId, Map<String, Object> u, LocalDate monday, long services, BigDecimal earned, BigDecimal prev,
                          String rating, long reviews, BigDecimal balance, long nextWeek) {
    String name = first(u.get("name"));
    String period = monday.minusDays(7).format(DateTimeFormatter.ofPattern("dd/MM")) + " a " + monday.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM"));
    String trend;
    if (prev == null || prev.signum() == 0) trend = "Boa semana! Continue assim para subir na lista de profissionais.";
    else {
      int pct = earned.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 0, java.math.RoundingMode.HALF_UP).intValue();
      trend = pct >= 0 ? "Você ganhou " + pct + "% a mais que na semana anterior. Muito bem!"
          : "Semana um pouco mais fraca que a anterior. Deixar a agenda aberta e responder rápido ajuda a receber mais pedidos.";
    }
    MailTemplate t = MailTemplate.pro()
        .preheader("Sua semana: " + services + " serviço(s) e " + money(earned) + " ganhos.")
        .hero("ganhos.jpg").kicker("Resumo da semana · " + period).title(name.isEmpty() ? "Sua semana no OPUS" : name + ", sua semana no OPUS")
        .stats(MailTemplate.rows("ganhos na semana", money(earned), "serviços feitos", String.valueOf(services), "sua nota", rating))
        .rich(trend);
    List<String[]> det = MailTemplate.rows("Saldo para sacar", money(balance), "Avaliações novas", String.valueOf(reviews), "Agendados para esta semana", String.valueOf(nextWeek));
    t.details(det)
        .button("Abrir minha carteira", MailTemplate.openApp(true, "carteira"))
        .small("Dica: profissionais que respondem em até 10 minutos recebem mais pedidos.")
        .unsubscribe(unsubscribeUrl(proId));
    email.send(Objects.toString(u.get("email")), "Sua semana: " + money(earned) + " em " + services + " serviço(s)", t);
  }

  // ---------- 4) profissional: cadastro aprovado / recusado / dinheiro na carteira ----------

  /** Veio do app do profissional ou já trabalha como profissional. */
  public boolean isPro(long userId) {
    try {
      Long n = jdbc.queryForObject("SELECT COUNT(*) FROM user_ u WHERE u.id = ? AND (u.signup_app = 'PRO' " +
          "OR EXISTS (SELECT 1 FROM product__professional pp WHERE pp.professional_id = u.id) OR EXISTS (SELECT 1 FROM order_ o WHERE o.professional_id = u.id))", Long.class, userId);
      return n != null && n > 0;
    } catch (Exception e) { return false; }
  }

  private Map<String, Object> person(long userId) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT u.id, u.name, u.email FROM user_ u WHERE u.id = ? AND u.deleted_at IS NULL AND u.email LIKE '%@%'", userId);
    return r.isEmpty() ? null : r.get(0);
  }

  public void verificationApproved(long userId) {
    try {
      Map<String, Object> u = person(userId);
      if (u == null || !mark(userId, "VERIF_OK", "")) return;
      boolean pro = isPro(userId);
      verificationApprovedEmail(u, pro);
      if (pro) newProNearby(userId);
    } catch (Exception e) { log.warn("E-mail de aprovação {}: {}", userId, e.getMessage()); }
  }

  public void verificationApprovedEmail(Map<String, Object> u, boolean pro) {
    String name = first(u.get("name"));
    MailTemplate t;
    if (pro) {
      t = MailTemplate.pro()
          .preheader("Sua identidade foi confirmada. Agora você aparece para os clientes.")
          .hero("pintura.jpg").kicker("Cadastro aprovado").title(name.isEmpty() ? "Você está verificado!" : name + ", você está verificado!")
          .p("Conferimos seus documentos e está tudo certo. A partir de agora seu perfil aparece com o selo de verificado e os clientes já podem contratar você.")
          .checklist(java.util.Arrays.asList(
              "**Fique online** no app para receber chamados perto de você.",
              "**Confira seus serviços e preços**: perfis completos recebem mais pedidos.",
              "**Responda rápido**: quem responde em até 10 minutos fecha mais serviços."))
          .button("Ficar online agora", MailTemplate.openApp(true, null));
    } else {
      t = MailTemplate.client()
          .preheader("Sua identidade foi confirmada.")
          .hero("alongamento.jpg").kicker("Identidade confirmada").title(name.isEmpty() ? "Tudo certo com a sua conta" : name + ", tudo certo com a sua conta")
          .p("Conferimos seus documentos e sua identidade está confirmada. Os profissionais veem que você é verificado, o que deixa tudo mais seguro para os dois lados.")
          .button("Abrir o OPUS", MailTemplate.openApp(false, null));
    }
    email.send(Objects.toString(u.get("email")), pro ? "Cadastro aprovado: você já pode receber clientes" : "Sua identidade foi confirmada", t);
  }

  private static final Map<String, String> STEP_NAMES = Map.of(
      "DOC_FRONT", "Foto da frente do documento", "DOC_BACK", "Foto do verso do documento",
      "FACE", "Fotos do rosto", "SELFIE_DOC", "Foto segurando o documento");

  public void verificationRejected(long userId, String reason, List<String> steps) {
    try {
      Map<String, Object> u = person(userId);
      if (u == null) return;
      // pode ser recusado mais de uma vez: no máximo 1 e-mail por dia
      if (!mark(userId, "VERIF_NO", LocalDate.now(Z).toString())) return;
      verificationRejectedEmail(u, isPro(userId), reason, steps);
    } catch (Exception e) { log.warn("E-mail de recusa {}: {}", userId, e.getMessage()); }
  }

  public void verificationRejectedEmail(Map<String, Object> u, boolean pro, String reason, List<String> steps) {
    List<String> items = new java.util.ArrayList<>();
    for (String st : steps) items.add(STEP_NAMES.getOrDefault(st, st));
    MailTemplate t = (pro ? MailTemplate.pro() : MailTemplate.client())
        .preheader("Falta pouco: precisamos que você envie de novo algumas fotos.")
        .kicker("Verificação de identidade").title("Precisamos de mais uma foto")
        .p("Olá" + (first(u.get("name")).isEmpty() ? "" : ", " + first(u.get("name"))) + "! Conferimos seu cadastro, mas não conseguimos confirmar tudo desta vez.")
        .alert("Motivo: " + Objects.toString(reason, ""), false)
        .p("Envie de novo só isto:")
        .checklist(items)
        .button("Enviar de novo", MailTemplate.openApp(pro, "verificacao"))
        .small("Dica: use um lugar bem iluminado, sem reflexo no documento, e deixe o rosto inteiro aparecendo.");
    email.send(Objects.toString(u.get("email")), "Falta pouco para confirmar seu cadastro", t);
  }

  /** Dinheiro entrou na carteira do profissional (serviço concluído ou etapa aprovada). */
  public void walletCredit(long proId, BigDecimal amount, String what) {
    if (!email.isConfigured() || amount == null) return;
    try {
      Map<String, Object> u = person(proId);
      if (u == null) return;
      BigDecimal in = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND status = 'DONE'", BigDecimal.class, proId);
      BigDecimal out = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'WITHDRAW' AND status <> 'FAILED'", BigDecimal.class, proId);
      walletCreditEmail(u, amount, what, (in == null ? BigDecimal.ZERO : in).subtract(out == null ? BigDecimal.ZERO : out));
    } catch (Exception e) { log.warn("E-mail de carteira {}: {}", proId, e.getMessage()); }
  }

  public void walletCreditEmail(Map<String, Object> u, BigDecimal amount, String what, BigDecimal balance) {
    String when = LocalDateTime.now(Z).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"));
    MailTemplate t = MailTemplate.pro()
        .preheader("Entrou " + money(amount) + " na sua carteira OPUS.")
        .hero("ganhos.jpg").kicker("Dinheiro na carteira").title("Caiu " + money(amount) + " na sua carteira")
        .p("Olá" + (first(u.get("name")).isEmpty() ? "" : ", " + first(u.get("name"))) + "! O pagamento foi liberado e já está disponível para sacar.")
        .details(MailTemplate.rows("Valor", money(amount), "Referente a", Objects.toString(what, "Serviço"), "Quando", when, "Saldo para sacar", money(balance)))
        .button("Sacar agora", MailTemplate.openApp(true, "sacar"))
        .small("O saque vai para o PIX no seu CPF ou CNPJ, ou para a sua conta bancária.");
    email.send(Objects.toString(u.get("email")), "Caiu " + money(amount) + " na sua carteira", t);
  }

  // ---------- 5) profissional que não terminou o cadastro ----------

  @Scheduled(cron = "0 0 11 * * *", zone = "America/Sao_Paulo")
  public void proOnboarding() {
    if (!email.isConfigured()) return;
    try {
      LocalDateTime now = LocalDateTime.now(Z);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT u.id, u.name, u.email, u.created_at_, u.avatar_url, u.pix_key, COALESCE(u.verification_status,'NONE') AS vs, " +
              " (SELECT COUNT(*) FROM product__professional pp WHERE pp.professional_id = u.id) AS services, " +
              " (SELECT COUNT(*) FROM bank_account_ b WHERE b.user_id = u.id) AS bank " +
              "FROM user_ u WHERE " + ACTIVE + " AND u.signup_app = 'PRO' AND u.created_at_ < ? AND u.created_at_ > ? " +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = u.id AND l.kind = 'ONBOARD' AND l.sent_at > ?) " +
              "ORDER BY u.id LIMIT 60",
          Timestamp.valueOf(now.minusDays(1)), Timestamp.valueOf(now.minusDays(15)), Timestamp.valueOf(now.minusHours(40)));
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        boolean docs = !"NONE".equals(r.get("vs")) && !"".equals(r.get("vs")) && !"REJECTED".equals(r.get("vs"));
        boolean services = ((Number) r.get("services")).longValue() > 0;
        boolean pay = (r.get("pix_key") != null && !r.get("pix_key").toString().isBlank()) || ((Number) r.get("bank")).longValue() > 0;
        boolean photo = r.get("avatar_url") != null && !r.get("avatar_url").toString().isBlank();
        if (docs && services && pay) continue; // já está tudo pronto
        long days = java.time.Duration.between(((Timestamp) r.get("created_at_")).toLocalDateTime(), now).toDays();
        String stage = days >= 7 ? "7" : days >= 3 ? "3" : "1";
        if (!mark(uid, "ONBOARD", stage)) continue;
        onboardingEmail(uid, r, docs, services, pay, photo);
      }
    } catch (Exception e) { log.warn("E-mail cadastro incompleto: {}", e.getMessage()); }
  }

  public void onboardingEmail(long uid, Map<String, Object> r, boolean docs, boolean services, boolean pay, boolean photo) {
    String name = first(r.get("name"));
    int done = (docs ? 1 : 0) + (services ? 1 : 0) + (pay ? 1 : 0) + (photo ? 1 : 0);
    List<String> todo = new java.util.ArrayList<>();
    if (!docs) todo.add("**Enviar seus documentos**: leva 2 minutos (foto do documento e uma selfie).");
    if (!services) todo.add("**Cadastrar seus serviços** com preço e uma descrição curta.");
    if (!pay) todo.add("**Dizer onde receber**: PIX no seu CPF ou CNPJ, ou conta bancária.");
    if (!photo) todo.add("**Colocar uma foto sua** no perfil.");
    MailTemplate t = MailTemplate.pro()
        .preheader("Faltam " + todo.size() + " passo(s) para você começar a receber clientes.")
        .hero("eletrica.jpg").kicker("Seu cadastro · " + done + " de 4 prontos").title(name.isEmpty() ? "Falta pouco para receber clientes" : name + ", falta pouco para receber clientes")
        .p("Clientes já estão procurando profissionais perto de você, mas seu perfil só aparece quando o cadastro estiver completo. Falta isto:")
        .checklist(todo)
        .button(docs ? "Completar meu cadastro" : "Enviar documentos agora", MailTemplate.openApp(true, docs ? null : "verificacao"))
        .small("Seus documentos ficam guardados com segurança e só a equipe do OPUS vê.")
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), name.isEmpty() ? "Falta pouco para você receber clientes" : name + ", falta pouco para você receber clientes", t);
  }

  // =====================================================================================
  // Jornada do cliente e do profissional (recibo, aniversário, cupons, indicação etc.)
  // =====================================================================================

  private static final String KM = "6371*ACOS(LEAST(1, COS(RADIANS(%1$s.latitude))*COS(RADIANS(%2$s.latitude))*COS(RADIANS(%2$s.longitude)-RADIANS(%1$s.longitude))+SIN(RADIANS(%1$s.latitude))*SIN(RADIANS(%2$s.latitude))))";

  private static String dm(Object d) {
    if (d == null) return "";
    LocalDate x = d instanceof java.sql.Date ? ((java.sql.Date) d).toLocalDate() : d instanceof Timestamp ? ((Timestamp) d).toLocalDateTime().toLocalDate() : null;
    return x == null ? "" : x.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
  }

  private static BigDecimal bd(Object o) { return o == null ? BigDecimal.ZERO : o instanceof BigDecimal ? (BigDecimal) o : new BigDecimal(o.toString()); }

  /** Cria um cupom só desta pessoa (código único, vale 1 vez). */
  private String personalCoupon(String prefix, int percent, int days, String description) {
    String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    java.security.SecureRandom r = new java.security.SecureRandom();
    for (int tries = 0; tries < 5; tries++) {
      StringBuilder c = new StringBuilder(prefix).append('-');
      for (int i = 0; i < 5; i++) c.append(chars.charAt(r.nextInt(chars.length())));
      try {
        jdbc.update("INSERT INTO coupon_ (code, description, percent, amount, min_order, valid_until, max_uses, used, one_per_user, active, created_at) VALUES (?, ?, ?, 0, 0, ?, 1, 0, TRUE, TRUE, NOW())",
            c.toString(), description, BigDecimal.valueOf(percent), java.sql.Date.valueOf(LocalDate.now(Z).plusDays(days)));
        return c.toString();
      } catch (Exception dup) { /* código repetido: tenta outro */ }
    }
    return null;
  }

  // ---------- 6) recibo do pagamento ----------

  @Scheduled(cron = "0 */5 * * * *", zone = "America/Sao_Paulo")
  public void receipts() {
    if (!email.isConfigured()) return;
    try {
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT o.id, o.customer_id, o.date, o.start_time, o.total_cost, o.service_value, COALESCE(o.discount_amount,0) AS discount, o.coupon_code, o.package_purchase_id, " +
              " u.name, u.email, p.name AS service, pr.name AS pro, t.asaas_payment_id AS payid " +
              "FROM order_ o JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id LEFT JOIN user_ pr ON pr.id = o.professional_id " +
              "LEFT JOIN transaction_ t ON t.order_id = o.id " +
              "WHERE o.pix_paid = TRUE AND o.created_at > ? AND u.deleted_at IS NULL AND u.email LIKE '%@%' " +
              "AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = o.customer_id AND l.kind = 'RECEIPT' AND l.ref = CAST(o.id AS varchar)) LIMIT 30",
          Timestamp.valueOf(LocalDateTime.now(Z).minusDays(2)));
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("customer_id")).longValue();
        if (!mark(uid, "RECEIPT", r.get("id").toString())) continue;
        receiptEmail(r);
      }
    } catch (Exception e) { log.warn("Recibos: {}", e.getMessage()); }
  }

  public void receiptEmail(Map<String, Object> r) {
    String oid = r.get("id").toString();
    BigDecimal total = bd(r.get("total_cost")), service = r.get("service_value") == null ? total : bd(r.get("service_value")), discount = bd(r.get("discount"));
    BigDecimal fee = total.subtract(service).max(BigDecimal.ZERO), paid = total.subtract(discount);
    boolean pkg = r.get("package_purchase_id") != null;
    String when = dm(r.get("date")) + (r.get("start_time") != null ? " às " + r.get("start_time").toString().substring(0, 5) : "");
    List<String[]> det = MailTemplate.rows("Serviço", Objects.toString(r.get("service"), "Serviço"));
    if (r.get("pro") != null) det.add(new String[]{"Profissional", Objects.toString(r.get("pro"))});
    if (!when.isBlank()) det.add(new String[]{"Data marcada", when});
    det.add(new String[]{"Valor do serviço", money(service)});
    if (fee.signum() > 0) det.add(new String[]{"Taxa de proteção OPUS", money(fee)});
    if (discount.signum() > 0) det.add(new String[]{"Desconto" + (r.get("coupon_code") != null ? " (" + r.get("coupon_code") + ")" : ""), "− " + money(discount)});
    det.add(new String[]{"Total pago", pkg ? "Aula do pacote" : money(paid)});
    det.add(new String[]{"Forma de pagamento", pkg ? "Pacote de aulas" : "PIX"});
    det.add(new String[]{"Pago em", LocalDateTime.now(Z).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))});
    det.add(new String[]{"Código do pedido", oid.substring(0, 8).toUpperCase()});
    MailTemplate t = MailTemplate.client()
        .preheader("Recebemos " + (pkg ? "seu pedido" : money(paid)) + ". O dinheiro fica guardado até o serviço terminar.")
        .kicker("Recibo de pagamento").title("Pagamento recebido")
        .p("Olá" + (first(r.get("name")).isEmpty() ? "" : ", " + first(r.get("name"))) + "! Este é o comprovante do seu pagamento.")
        .details(det)
        .alert("Seu dinheiro fica guardado pelo OPUS e só vai para o profissional depois que você confirmar que o serviço terminou. Se algo der errado, você recebe de volta.", false)
        .button("Ver recibo no app", MailTemplate.openApp(false, "recibo/" + oid))
        .small("Este e-mail é um comprovante de pagamento e não substitui nota fiscal.");
    email.send(Objects.toString(r.get("email")), "Recibo: " + Objects.toString(r.get("service"), "seu pedido") + (pkg ? "" : " · " + money(paid)), t);
  }

  // ---------- 7) aniversário (com cupom) ----------

  @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
  public void birthdays() {
    if (!email.isConfigured()) return;
    try {
      LocalDate today = LocalDate.now(Z);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT u.id, u.name, u.email FROM user_ u WHERE " + ACTIVE + " AND u.birthday IS NOT NULL AND EXTRACT(MONTH FROM u.birthday) = ? AND EXTRACT(DAY FROM u.birthday) = ? LIMIT 100",
          today.getMonthValue(), today.getDayOfMonth());
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        if (!mark(uid, "BIRTHDAY", String.valueOf(today.getYear()))) continue;
        int pct = setting(SettingsService.BIRTHDAY_COUPON_PERCENT, 15);
        String code = pct > 0 ? personalCoupon("NIVER", pct, 15, "Aniversário de " + first(r.get("name"))) : null;
        birthdayEmail(uid, r, code, pct);
      }
    } catch (Exception e) { log.warn("Aniversários: {}", e.getMessage()); }
  }

  public void birthdayEmail(long uid, Map<String, Object> r, String code, int pct) {
    String name = first(r.get("name"));
    MailTemplate t = MailTemplate.client()
        .preheader("Hoje o dia é seu! Preparamos um presente.")
        .hero("alongamento.jpg").kicker("Feliz aniversário").title(name.isEmpty() ? "Parabéns pelo seu dia!" : "Parabéns, " + name + "!")
        .p("Toda a equipe do OPUS deseja um ano cheio de saúde, conquistas e momentos bons. Obrigado por fazer parte do OPUS.");
    if (code != null) t.p("Para comemorar, um presente para você usar quando quiser nos próximos 15 dias:")
        .coupon(code, pct + "% de desconto", "Use na tela de pagamento · vale até " + LocalDate.now(Z).plusDays(15).format(DateTimeFormatter.ofPattern("dd/MM")))
        .button("Usar meu presente", MailTemplate.openApp(false, null));
    t.unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), name.isEmpty() ? "Feliz aniversário! Tem presente para você" : "Feliz aniversário, " + name + "! Tem presente para você", t);
  }

  // ---------- 8) profissional novo perto de você ----------

  /** Chamado quando um profissional é aprovado: avisa clientes da região que já contrataram a mesma categoria. */
  public void newProNearby(long proId) {
    if (!email.isConfigured()) return;
    try {
      List<Map<String, Object>> pro = jdbc.queryForList("SELECT u.id, u.name, u.latitude, u.longitude, " +
          " (SELECT STRING_AGG(DISTINCT p.name, ', ') FROM product__professional pp JOIN product_ p ON p.id = pp.product_id WHERE pp.professional_id = u.id) AS services " +
          "FROM user_ u WHERE u.id = ? AND u.latitude IS NOT NULL", proId);
      if (pro.isEmpty() || pro.get(0).get("services") == null) return;
      Map<String, Object> pr = pro.get(0);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT DISTINCT u.id, u.name, u.email, c.name AS category FROM user_ u " +
              "JOIN order_ o ON o.customer_id = u.id JOIN product_ op ON op.id = o.product_id JOIN category_ c ON c.id = op.category_id " +
              "JOIN product_ mp ON mp.category_id = op.category_id JOIN product__professional pp ON pp.product_id = mp.id AND pp.professional_id = ? " +
              "JOIN user_ me ON me.id = ? " +
              "WHERE " + ACTIVE + " AND u.id <> ? AND u.latitude IS NOT NULL AND " + String.format(KM, "u", "me") + " <= 15 " +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = u.id AND l.kind = 'NEWPRO' AND l.sent_at > ?) LIMIT 60",
          proId, proId, proId, Timestamp.valueOf(LocalDateTime.now(Z).minusDays(14)));
      java.util.Set<Long> done = new java.util.HashSet<>();
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        if (!done.add(uid) || !mark(uid, "NEWPRO", String.valueOf(proId))) continue;
        newProEmail(uid, r, pr);
      }
    } catch (Exception e) { log.warn("Profissional novo perto: {}", e.getMessage()); }
  }

  public void newProEmail(long uid, Map<String, Object> r, Map<String, Object> pro) {
    String cat = Objects.toString(r.get("category"), "serviços");
    MailTemplate t = MailTemplate.client()
        .preheader(first(pro.get("name")) + " acabou de chegar ao OPUS perto de você.")
        .hero("pintura.jpg").kicker("Novo perto de você").title("Chegou profissional de " + cat.toLowerCase(new Locale("pt", "BR")) + " na sua região")
        .p("Olá" + (first(r.get("name")).isEmpty() ? "" : ", " + first(r.get("name"))) + "! Como você já contratou " + cat.toLowerCase(new Locale("pt", "BR")) + " pelo OPUS, achamos que vai gostar de saber:")
        .person(Objects.toString(pro.get("name")), "Verificado · " + Objects.toString(pro.get("services"), ""), MailTemplate.openApp(false, "professionals/" + pro.get("id")))
        .gap()
        .p("Ele teve a identidade conferida pela nossa equipe. Profissional novo costuma ter horários livres, então é uma boa hora para marcar.")
        .button("Ver agenda", MailTemplate.openApp(false, "professionals/" + pro.get("id")))
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), "Novo profissional de " + cat.toLowerCase(new Locale("pt", "BR")) + " perto de você", t);
  }

  // ---------- 9) profissional parado há 14 dias ----------

  @Scheduled(cron = "0 0 15 * * *", zone = "America/Sao_Paulo")
  public void idlePros() {
    if (!email.isConfigured()) return;
    try {
      Timestamp d14 = Timestamp.valueOf(LocalDateTime.now(Z).minusDays(14));
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT u.id, u.name, u.email FROM user_ u JOIN professional_ p ON p.id = u.professional_id WHERE " + ACTIVE +
              " AND u.verification_status = 'APPROVED' AND COALESCE(p.is_online,FALSE) = FALSE " +
              " AND COALESCE(p.online_at, (SELECT MAX(d.last_seen) FROM user_device_ d WHERE d.user_id = u.id), u.created_at_, '2000-01-01') < ? " +
              " AND NOT EXISTS (SELECT 1 FROM order_ o WHERE o.professional_id = u.id AND o.created_at > ?) " +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = u.id AND l.kind = 'IDLEPRO' AND l.sent_at > ?) LIMIT 60",
          d14, d14, Timestamp.valueOf(LocalDateTime.now(Z).minusDays(30)));
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        if (!mark(uid, "IDLEPRO", LocalDate.now(Z).toString())) continue;
        List<Map<String, Object>> dem = jdbc.queryForList(
            "SELECT p.name, COUNT(o.id) AS n FROM product__professional pp JOIN product_ p ON p.id = pp.product_id " +
                "LEFT JOIN order_ o ON o.product_id = p.id AND o.created_at > ? WHERE pp.professional_id = ? GROUP BY p.name ORDER BY n DESC LIMIT 3", d14, uid);
        idleProEmail(uid, r, dem);
      }
    } catch (Exception e) { log.warn("Profissional parado: {}", e.getMessage()); }
  }

  public void idleProEmail(long uid, Map<String, Object> r, List<Map<String, Object>> demand) {
    String name = first(r.get("name"));
    long total = 0;
    for (Map<String, Object> d : demand) total += ((Number) d.get("n")).longValue();
    MailTemplate t = MailTemplate.pro()
        .preheader("Faz duas semanas que você não fica online. Os clientes continuam procurando.")
        .hero("eletrica.jpg").kicker("Sentimos sua falta").title(name.isEmpty() ? "Os clientes estão procurando você" : name + ", os clientes estão procurando você")
        .p("Faz mais de duas semanas que você não fica online no OPUS. Enquanto isso, os pedidos continuaram chegando.");
    if (total >= 3) {
      List<String[]> st = new java.util.ArrayList<>();
      for (Map<String, Object> d : demand) if (((Number) d.get("n")).longValue() > 0) st.add(new String[]{Objects.toString(d.get("name")), String.valueOf(d.get("n"))});
      t.p("Pedidos dos seus serviços nas últimas 2 semanas:").stats(st);
    }
    t.checklist(java.util.Arrays.asList(
            "**Fique online** nos horários em que pode atender. Você escolhe quando parar.",
            "**Confira sua agenda e sua área de atendimento** para receber só o que faz sentido.",
            "**Responda rápido**: quem responde em até 10 minutos aparece primeiro."))
        .button("Ficar online agora", MailTemplate.openApp(true, null))
        .small("Não está mais atendendo? Você pode pausar o perfil no app, em Perfil.")
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), name.isEmpty() ? "Os clientes estão procurando você" : name + ", os clientes estão procurando você", t);
  }

  // ---------- 10) "quer marcar de novo?" ----------

  @Scheduled(cron = "0 0 18 * * *", zone = "America/Sao_Paulo")
  public void rebook() {
    if (!email.isConfigured()) return;
    try {
      LocalDateTime now = LocalDateTime.now(Z);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT o.id, o.customer_id, o.professional_id, u.name, u.email, pr.name AS pro, p.name AS service, " +
              " (SELECT MAX(rv.rating) FROM review_ rv WHERE rv.order_id = o.id) AS rating " +
              "FROM order_ o JOIN user_ u ON u.id = o.customer_id JOIN user_ pr ON pr.id = o.professional_id LEFT JOIN product_ p ON p.id = o.product_id " +
              "WHERE o.status IN ('DONE','REVIEWED') AND o.done_at BETWEEN ? AND ? AND " + ACTIVE +
              " AND pr.deleted_at IS NULL AND COALESCE(pr.blocked,false) = false " +
              " AND NOT EXISTS (SELECT 1 FROM order_ o2 WHERE o2.customer_id = o.customer_id AND o2.created_at > o.done_at AND o2.status <> 'CANCELLED') " +
              " AND NOT EXISTS (SELECT 1 FROM pro_blocked_client_ b WHERE b.professional_id = o.professional_id AND b.client_id = o.customer_id) " +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = o.customer_id AND l.kind = 'REBOOK' AND l.sent_at > ?) " +
              "ORDER BY o.done_at LIMIT 80",
          Timestamp.valueOf(now.minusDays(7)), Timestamp.valueOf(now.minusDays(5)), Timestamp.valueOf(now.minusDays(10)));
      java.util.Set<Long> seen = new java.util.HashSet<>();
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("customer_id")).longValue();
        Object rating = r.get("rating");
        if (rating != null && ((Number) rating).intValue() < 4) continue; // não foi bem avaliado: não sugere de novo
        if (!seen.add(uid) || !mark(uid, "REBOOK", r.get("id").toString())) continue;
        rebookEmail(uid, r);
      }
    } catch (Exception e) { log.warn("Marcar de novo: {}", e.getMessage()); }
  }

  public void rebookEmail(long uid, Map<String, Object> r) {
    String pro = first(r.get("pro")), svc = Objects.toString(r.get("service"), "o serviço");
    MailTemplate t = MailTemplate.client()
        .preheader("Gostou de " + svc.toLowerCase(new Locale("pt", "BR")) + " com " + pro + "? Marque o próximo em poucos toques.")
        .hero("boxe.jpg").kicker("Próximo horário").title("Quer marcar de novo com " + pro + "?")
        .p("Olá" + (first(r.get("name")).isEmpty() ? "" : ", " + first(r.get("name"))) + "! Já faz uma semana desde o último atendimento com " + pro + " (" + svc.toLowerCase(new Locale("pt", "BR")) + "). Quem mantém a frequência vê resultado mais rápido.")
        .person(Objects.toString(r.get("pro")), svc, MailTemplate.openApp(false, "professionals/" + r.get("professional_id")))
        .gap()
        .button("Marcar de novo", MailTemplate.openApp(false, "professionals/" + r.get("professional_id")))
        .small("Dica: no perfil do profissional você pode comprar um pacote de aulas e pagar menos por cada uma.")
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), "Quer marcar de novo com " + pro + "?", t);
  }

  // ---------- 11) cupom do primeiro pedido ----------

  @Scheduled(cron = "0 30 12 * * *", zone = "America/Sao_Paulo")
  public void welcomeCoupon() {
    if (!email.isConfigured()) return;
    int pct = setting(SettingsService.WELCOME_COUPON_PERCENT, 10);
    if (pct <= 0) return;
    try {
      LocalDateTime now = LocalDateTime.now(Z);
      List<Map<String, Object>> rows = jdbc.queryForList(
          "SELECT u.id, u.name, u.email FROM user_ u WHERE " + ACTIVE + " AND COALESCE(u.signup_app,'CLIENT') <> 'PRO' " +
              " AND u.created_at_ BETWEEN ? AND ? AND NOT EXISTS (SELECT 1 FROM order_ o WHERE o.customer_id = u.id) " +
              " AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = u.id AND l.kind = 'WELCOMECOUPON') LIMIT 80",
          Timestamp.valueOf(now.minusDays(5)), Timestamp.valueOf(now.minusDays(3)));
      for (Map<String, Object> r : rows) {
        long uid = ((Number) r.get("id")).longValue();
        if (!mark(uid, "WELCOMECOUPON", "")) continue;
        String code = personalCoupon("BEMVINDO", pct, 7, "Primeiro pedido de " + first(r.get("name")));
        if (code != null) welcomeCouponEmail(uid, r, code, pct);
      }
    } catch (Exception e) { log.warn("Cupom de primeiro pedido: {}", e.getMessage()); }
  }

  public void welcomeCouponEmail(long uid, Map<String, Object> r, String code, int pct) {
    String name = first(r.get("name"));
    MailTemplate t = MailTemplate.client()
        .preheader(pct + "% de desconto no seu primeiro pedido. Vale por 7 dias.")
        .hero("grama.jpg").kicker("Presente de boas-vindas").title(name.isEmpty() ? "Seu primeiro pedido com desconto" : name + ", seu primeiro pedido com desconto")
        .p("Você criou sua conta e ainda não experimentou o OPUS. Para começar bem, separamos um desconto:")
        .coupon(code, pct + "% de desconto", "No primeiro pedido · vale até " + LocalDate.now(Z).plusDays(7).format(DateTimeFormatter.ofPattern("dd/MM")))
        .checklist(java.util.Arrays.asList(
            "Escolha o serviço e o profissional.",
            "Na tela de pagamento, digite o código em **Tem um cupom?**",
            "Pague com PIX. O dinheiro fica guardado até o serviço terminar."))
        .button("Escolher um profissional", MailTemplate.openApp(false, null))
        .unsubscribe(unsubscribeUrl(uid));
    email.send(Objects.toString(r.get("email")), pct + "% de desconto no seu primeiro pedido", t);
  }

  // ---------- 12) indicação ----------

  @Scheduled(cron = "0 */30 * * * *", zone = "America/Sao_Paulo")
  public void referrals() {
    if (!email.isConfigured()) return;
    try {
      LocalDateTime now = LocalDateTime.now(Z);
      int pts = setting(SettingsService.REFERRAL_POINTS, 100);
      for (Map<String, Object> r : jdbc.queryForList(
          "SELECT r.referrer_id, r.referred_id, a.name, a.email, b.name AS friend FROM referral_ r JOIN user_ a ON a.id = r.referrer_id JOIN user_ b ON b.id = r.referred_id " +
              "WHERE r.created_at > ? AND a.deleted_at IS NULL AND a.email LIKE '%@%' " +
              "AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = r.referrer_id AND l.kind = 'REF_JOIN' AND l.ref = CAST(r.referred_id AS varchar)) LIMIT 50",
          Timestamp.valueOf(now.minusDays(2)))) {
        long uid = ((Number) r.get("referrer_id")).longValue();
        if (!mark(uid, "REF_JOIN", r.get("referred_id").toString())) continue;
        referralEmail(r, false, pts);
      }
      for (Map<String, Object> r : jdbc.queryForList(
          "SELECT r.referrer_id, r.referred_id, a.name, a.email, b.name AS friend FROM referral_ r JOIN user_ a ON a.id = r.referrer_id JOIN user_ b ON b.id = r.referred_id " +
              "WHERE r.rewarded_at > ? AND a.deleted_at IS NULL AND a.email LIKE '%@%' " +
              "AND NOT EXISTS (SELECT 1 FROM email_log_ l WHERE l.user_id = r.referrer_id AND l.kind = 'REF_REWARD' AND l.ref = CAST(r.referred_id AS varchar)) LIMIT 50",
          Timestamp.valueOf(now.minusDays(3)))) {
        long uid = ((Number) r.get("referrer_id")).longValue();
        if (!mark(uid, "REF_REWARD", r.get("referred_id").toString())) continue;
        referralEmail(r, true, pts);
      }
    } catch (Exception e) { log.warn("E-mails de indicação: {}", e.getMessage()); }
  }

  public void referralEmail(Map<String, Object> r, boolean rewarded, int pts) {
    String friend = first(r.get("friend"));
    if (friend.isEmpty()) friend = "Seu amigo";
    MailTemplate t = MailTemplate.client().hero(rewarded ? "pagamento.jpg" : "grama.jpg").kicker("Indique e ganhe");
    if (rewarded) {
      t.preheader("Você ganhou " + pts + " pontos pela indicação de " + friend + ".")
          .title("Você ganhou " + pts + " pontos!")
          .p(friend + " fez o primeiro serviço pelo OPUS com o seu código. Como combinado, os " + pts + " pontos já estão na sua conta.")
          .alert("Seus pontos viram desconto nos próximos pedidos. É só escolher usar os pontos na hora de pagar.", false)
          .button("Ver meus pontos", MailTemplate.openApp(false, "carteira"))
          .p("Quanto mais amigos você indicar, mais pontos você ganha.");
    } else {
      t.preheader(friend + " entrou no OPUS com o seu código.")
          .title(friend + " entrou com o seu código!")
          .p("Obrigado por indicar o OPUS. Assim que " + friend + " fizer o primeiro serviço, vocês dois ganham " + pts + " pontos.")
          .button("Indicar mais amigos", MailTemplate.openApp(false, "indique"));
    }
    email.send(Objects.toString(r.get("email")), rewarded ? "Você ganhou " + pts + " pontos pela indicação" : friend + " entrou no OPUS com o seu código", t);
  }

  // ---------- 13) profissional cancelou: sugere outros ----------

  public void proCancelled(java.util.UUID orderId) {
    if (!email.isConfigured()) return;
    try {
      List<Map<String, Object>> os = jdbc.queryForList(
          "SELECT o.id, o.customer_id, o.professional_id, o.product_id, o.date, o.start_time, u.name, u.email, p.name AS service, pr.name AS pro " +
              "FROM order_ o JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id LEFT JOIN user_ pr ON pr.id = o.professional_id " +
              "WHERE o.id = ? AND u.deleted_at IS NULL AND u.email LIKE '%@%'", orderId);
      if (os.isEmpty()) return;
      Map<String, Object> o = os.get(0);
      long uid = ((Number) o.get("customer_id")).longValue();
      if (!mark(uid, "PROCANCEL", orderId.toString())) return;
      List<Map<String, Object>> alts = o.get("product_id") == null ? java.util.Collections.emptyList() : jdbc.queryForList(
          "SELECT u.id, u.name, (SELECT AVG(rv.rating) FROM review_ rv WHERE rv.professional_id = u.id) AS rating, " +
              " (SELECT COUNT(*) FROM order_ x WHERE x.professional_id = u.id AND x.status IN ('DONE','REVIEWED')) AS done, " +
              " CASE WHEN c.latitude IS NULL OR u.latitude IS NULL THEN NULL ELSE " + String.format(KM, "c", "u") + " END AS km " +
              "FROM product__professional pp JOIN user_ u ON u.id = pp.professional_id JOIN user_ c ON c.id = ? " +
              "WHERE pp.product_id = ? AND u.id <> ? AND u.verification_status = 'APPROVED' AND u.deleted_at IS NULL AND COALESCE(u.blocked,false) = false " +
              "ORDER BY km NULLS LAST, rating DESC NULLS LAST LIMIT 3",
          uid, o.get("product_id"), o.get("professional_id"));
      proCancelledEmail(o, alts);
    } catch (Exception e) { log.warn("Profissional cancelou {}: {}", orderId, e.getMessage()); }
  }

  public void proCancelledEmail(Map<String, Object> o, List<Map<String, Object>> alts) {
    String svc = Objects.toString(o.get("service"), "seu serviço");
    String when = dm(o.get("date")) + (o.get("start_time") != null ? " às " + o.get("start_time").toString().substring(0, 5) : "");
    MailTemplate t = MailTemplate.client()
        .preheader("O profissional não vai poder atender. Separamos outras opções para você.")
        .kicker("Pedido cancelado").title("O profissional precisou cancelar")
        .p("Olá" + (first(o.get("name")).isEmpty() ? "" : ", " + first(o.get("name"))) + "! " + first(o.get("pro")) + " cancelou o pedido de " + svc.toLowerCase(new Locale("pt", "BR"))
            + (when.isBlank() ? "" : " marcado para " + when) + ". Pedimos desculpas pelo transtorno.")
        .alert("Se você já pagou, o valor volta inteiro para você, sem nenhum desconto.", false);
    if (!alts.isEmpty()) {
      t.p("Para você não ficar sem atendimento, separamos profissionais verificados que fazem o mesmo serviço:");
      for (Map<String, Object> a : alts) {
        StringBuilder info = new StringBuilder();
        if (a.get("rating") != null) info.append("★ ").append(String.format(BR, "%.1f", ((Number) a.get("rating")).doubleValue())).append(" · ");
        long done = ((Number) a.get("done")).longValue();
        info.append(done > 0 ? done + " serviço(s) feito(s)" : "Novo no OPUS");
        if (a.get("km") != null) info.append(" · ").append(String.format(BR, "%.1f km", ((Number) a.get("km")).doubleValue()));
        t.person(Objects.toString(a.get("name")), info.toString(), MailTemplate.openApp(false, "professionals/" + a.get("id")));
      }
      t.gap();
    }
    t.button("Escolher outro profissional", MailTemplate.openApp(false, null));
    email.send(Objects.toString(o.get("email")), "Seu pedido foi cancelado pelo profissional", t);
  }

  // ---------- 14) saque feito ou com problema ----------

  public void withdrawResult(long proId, BigDecimal amount, String dest, boolean ok) {
    if (!email.isConfigured()) return;
    try {
      Map<String, Object> u = person(proId);
      if (u != null) withdrawEmail(u, amount, dest, ok);
    } catch (Exception e) { log.warn("E-mail de saque {}: {}", proId, e.getMessage()); }
  }

  public void withdrawEmail(Map<String, Object> u, BigDecimal amount, String dest, boolean ok) {
    String when = LocalDateTime.now(Z).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"));
    MailTemplate t = MailTemplate.pro();
    if (ok) {
      t.preheader("Seu saque de " + money(amount) + " foi enviado.")
          .hero("ganhos.jpg").kicker("Saque feito").title("Seu saque de " + money(amount) + " foi enviado")
          .p("Olá" + (first(u.get("name")).isEmpty() ? "" : ", " + first(u.get("name"))) + "! Mandamos o dinheiro para você.")
          .details(MailTemplate.rows("Valor", money(amount), "Para", Objects.toString(dest, ""), "Quando", when))
          .small("PIX costuma cair na hora. Conta bancária pode levar até 1 dia útil.")
          .alert("Não pediu este saque? Abra o app agora em Perfil → Segurança → Não fui eu, e fale com o suporte.", true);
    } else {
      t.preheader("Não conseguimos fazer o seu saque. O dinheiro continua na sua carteira.")
          .kicker("Saque não feito").title("Não conseguimos enviar o seu saque")
          .p("Olá" + (first(u.get("name")).isEmpty() ? "" : ", " + first(u.get("name"))) + "! O saque de " + money(amount) + " para " + Objects.toString(dest, "a sua conta") + " não foi concluído.")
          .alert("Fique tranquilo: o dinheiro continua na sua carteira do OPUS.", false)
          .checklist(java.util.Arrays.asList(
              "Confira se a chave PIX ou a conta está no **seu CPF ou no seu CNPJ**.",
              "Confira os números da agência e da conta.",
              "Tente sacar de novo pelo app."))
          .button("Conferir onde eu recebo", MailTemplate.openApp(true, "recebimento"));
    }
    email.send(Objects.toString(u.get("email")), ok ? "Saque de " + money(amount) + " enviado" : "Seu saque não foi concluído", t);
  }

  // ---------- 15) extrato do mês para o MEI ----------

  @Scheduled(cron = "0 30 7 1 * *", zone = "America/Sao_Paulo")
  public void monthlyStatements() {
    if (!email.isConfigured()) return;
    LocalDate first = LocalDate.now(Z).minusMonths(1).withDayOfMonth(1);
    try {
      List<Map<String, Object>> pros = jdbc.queryForList(
          "SELECT DISTINCT w.professional_id FROM wallet_entry_ w WHERE w.kind IN ('CREDIT','STAGE') AND w.status = 'DONE' AND w.created_at >= ? AND w.created_at < ?",
          Timestamp.valueOf(first.atStartOfDay()), Timestamp.valueOf(first.plusMonths(1).atStartOfDay()));
      for (Map<String, Object> p : pros) monthlyStatement(((Number) p.get("professional_id")).longValue(), first);
    } catch (Exception e) { log.warn("Extratos do mês: {}", e.getMessage()); }
  }

  public void monthlyStatement(long proId, LocalDate month) {
    try {
      Map<String, Object> u = person(proId);
      if (u == null || !mark(proId, "STATEMENT", month.toString().substring(0, 7))) return;
      Timestamp a = Timestamp.valueOf(month.atStartOfDay()), b = Timestamp.valueOf(month.plusMonths(1).atStartOfDay());
      List<Map<String, Object>> lines = jdbc.queryForList(
          "SELECT w.created_at, w.description, w.amount, w.kind FROM wallet_entry_ w WHERE w.professional_id = ? AND w.kind IN ('CREDIT','STAGE') AND w.status = 'DONE' AND w.created_at >= ? AND w.created_at < ? ORDER BY w.created_at",
          proId, a, b);
      BigDecimal out = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'WITHDRAW' AND status = 'DONE' AND created_at >= ? AND created_at < ?",
          BigDecimal.class, proId, a, b);
      BigDecimal year = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND status = 'DONE' AND created_at >= ? AND created_at < ?",
          BigDecimal.class, proId, Timestamp.valueOf(month.withDayOfYear(1).atStartOfDay()), b);
      statementEmail(u, month, lines, out == null ? BigDecimal.ZERO : out, year == null ? BigDecimal.ZERO : year);
    } catch (Exception e) { log.warn("Extrato {}: {}", proId, e.getMessage()); }
  }

  public void statementEmail(Map<String, Object> u, LocalDate month, List<Map<String, Object>> lines, BigDecimal withdrawn, BigDecimal year) {
    String mes = month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", BR));
    BigDecimal total = BigDecimal.ZERO;
    StringBuilder csv = new StringBuilder("﻿Data;Descrição;Valor\r\n");
    List<String[]> rows = new java.util.ArrayList<>();
    for (Map<String, Object> l : lines) {
      BigDecimal v = bd(l.get("amount"));
      total = total.add(v);
      String d = ((Timestamp) l.get("created_at")).toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      String desc = Objects.toString(l.get("description"), "Serviço").replace(";", ",");
      csv.append(d).append(';').append(desc).append(';').append(v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')).append("\r\n");
      if (rows.size() < 25) rows.add(new String[]{d.substring(0, 5) + " · " + desc, money(v)});
    }
    csv.append(";Total do mês;").append(total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')).append("\r\n");
    BigDecimal limit = new BigDecimal("81000");
    int pctYear = year.multiply(BigDecimal.valueOf(100)).divide(limit, 0, java.math.RoundingMode.HALF_UP).intValue();
    MailTemplate t = MailTemplate.pro()
        .preheader("Você recebeu " + money(total) + " em " + mes + ". O extrato completo vai em anexo.")
        .kicker("Extrato do mês").title("Seu extrato de " + mes)
        .stats(MailTemplate.rows("recebido no mês", money(total), "serviços e etapas", String.valueOf(lines.size()), "sacado no mês", money(withdrawn)))
        .p("Lançamentos do mês:")
        .details(rows);
    if (lines.size() > rows.size()) t.small("E mais " + (lines.size() - rows.size()) + " lançamento(s) na planilha em anexo.");
    t.rich("**No ano até agora: " + money(year) + "**. Para o MEI, o limite é de R$ 81.000,00 por ano (você está em " + pctYear + "%).");
    if (pctYear >= 80) t.alert("Atenção: você já passou de 80% do limite anual do MEI. Converse com um contador.", true);
    t.small("A planilha em anexo abre no Excel ou no Google Planilhas e serve para guardar o histórico e declarar o faturamento do MEI. Este extrato não substitui nota fiscal.")
        .button("Ver minha carteira", MailTemplate.openApp(true, "carteira"));
    email.send(Objects.toString(u.get("email")), "Seu extrato de " + mes + ": " + money(total), t,
        "extrato-opus-" + month.toString().substring(0, 7) + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8), "text/csv");
  }

  // ---------- 16) senha ou e-mail trocados ----------

  public void passwordChanged(long userId) {
    try {
      Map<String, Object> u = person(userId);
      if (u != null) securityChangeEmail(Objects.toString(u.get("email")), u, "Sua senha foi trocada", "A senha da sua conta OPUS foi trocada agora (" + nowText() + ").");
    } catch (Exception e) { log.warn("Aviso de senha {}: {}", userId, e.getMessage()); }
  }

  /** Avisa o e-mail ANTIGO (é ele que interessa se alguém trocou sem a pessoa saber) e o novo. */
  public void emailChanged(long userId, String oldEmail, String newEmail) {
    try {
      Map<String, Object> u = new java.util.HashMap<>();
      List<Map<String, Object>> r = jdbc.queryForList("SELECT name FROM user_ WHERE id = ?", userId);
      u.put("name", r.isEmpty() ? "" : r.get(0).get("name"));
      String text = "O e-mail da sua conta OPUS foi trocado de " + maskEmail(oldEmail) + " para " + maskEmail(newEmail) + " (" + nowText() + ").";
      securityChangeEmail(oldEmail, u, "O e-mail da sua conta foi trocado", text);
      securityChangeEmail(newEmail, u, "O e-mail da sua conta foi trocado", text);
    } catch (Exception e) { log.warn("Aviso de e-mail {}: {}", userId, e.getMessage()); }
  }

  private static String nowText() { return LocalDateTime.now(Z).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")); }

  public static String maskEmail(String e) {
    if (e == null || !e.contains("@")) return "";
    String[] p = e.split("@", 2);
    String n = p[0].length() <= 2 ? p[0].charAt(0) + "•" : p[0].substring(0, 2) + "•••";
    return n + "@" + p[1];
  }

  public void securityChangeEmail(String to, Map<String, Object> u, String title, String text) {
    if (to == null || !to.contains("@")) return;
    email.sendSecurityAlert(to, Objects.toString(u.get("name"), ""), title + " · OPUS", title,
        text + "\nFoi você? Então está tudo certo.\nNão foi você? Abra o app agora e troque a senha em Perfil → Segurança, ou responda este e-mail que a equipe bloqueia a conta para você.");
  }
}
