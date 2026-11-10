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
}
