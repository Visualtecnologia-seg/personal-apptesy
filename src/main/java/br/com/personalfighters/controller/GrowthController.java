package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Points;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.PointsRepository;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.SettingsService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/** Pacotes de aulas, cupons, indique e ganhe, selo Top OPUS e lembretes automáticos. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GrowthController {

  private static final ZoneId BR = ZoneId.of("America/Sao_Paulo");

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final TransactionRepository transactionRepository;
  private final PointsRepository pointsRepository;
  private final AsaasService asaasService;
  private final NotificationService notificationService;
  private final SettingsService settings;

  // ───────────── Pacotes de aulas ─────────────

  @GetMapping("/professionals/{proId}/packages")
  public List<Map<String, Object>> packages(@PathVariable Long proId) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, title, sessions, hours_each AS \"hoursEach\", price FROM lesson_package_ WHERE professional_id = ? AND active = TRUE ORDER BY sessions", proId);
    BigDecimal rate = hourlyRate(proId);
    for (Map<String, Object> r : rows) {
      BigDecimal price = (BigDecimal) r.get("price");
      int sessions = ((Number) r.get("sessions")).intValue();
      int hours = ((Number) r.get("hoursEach")).intValue();
      r.put("clientTotal", Pricing.clientTotal(price));
      r.put("perSession", Pricing.clientTotal(price).divide(BigDecimal.valueOf(sessions), 2, RoundingMode.HALF_UP));
      if (rate != null && rate.signum() > 0) {
        BigDecimal full = rate.multiply(BigDecimal.valueOf((long) sessions * hours));
        r.put("savingPercent", full.signum() == 0 ? 0 : BigDecimal.ONE.subtract(price.divide(full, 4, RoundingMode.HALF_UP)).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP));
      }
    }
    return rows;
  }

  @PostMapping("/packages")
  public ResponseEntity<?> createPackage(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (!SocialController.hasRole(me, Role.PROFESSIONAL)) return bad("Só profissionais criam pacotes.");
    int sessions = intOf(b.get("sessions"), 0);
    int hours = Math.max(1, intOf(b.get("hoursEach"), 1));
    if (sessions < 2 || sessions > 60) return bad("O pacote precisa ter de 2 a 60 aulas.");
    BigDecimal price;
    try { price = new BigDecimal(Objects.toString(b.get("price"), "").replace(',', '.')).setScale(2, RoundingMode.HALF_UP); } catch (Exception e) { return bad("Preço inválido."); }
    BigDecimal rate = hourlyRate(me.getId());
    if (rate != null && rate.signum() > 0) {
      BigDecimal full = rate.multiply(BigDecimal.valueOf((long) sessions * hours));
      BigDecimal maxDisc = new BigDecimal(settings.get(SettingsService.PACKAGE_MAX_DISCOUNT)).divide(BigDecimal.valueOf(100));
      if (price.compareTo(full.multiply(BigDecimal.ONE.subtract(maxDisc))) < 0) return bad("O desconto máximo do pacote é " + settings.get(SettingsService.PACKAGE_MAX_DISCOUNT) + "%.");
      if (price.compareTo(full) > 0) return bad("O pacote não pode custar mais que as aulas avulsas (R$ " + full.setScale(2) + ").");
    }
    String title = Objects.toString(b.get("title"), "").trim();
    if (title.isEmpty()) title = sessions + " aulas";
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO lesson_package_ (id, professional_id, title, sessions, hours_each, price, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
        id, me.getId(), title, sessions, hours, price, Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(packages(me.getId()));
  }

  @DeleteMapping("/packages/{id}")
  public ResponseEntity<?> removePackage(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("UPDATE lesson_package_ SET active = FALSE WHERE id = ? AND professional_id = ?", id, me.getId());
    return ResponseEntity.ok(packages(me.getId()));
  }

  @PostMapping("/packages/{id}/buy")
  public ResponseEntity<?> buy(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM lesson_package_ WHERE id = ? AND active = TRUE", id);
    if (r.isEmpty()) return bad("Pacote não encontrado.");
    Map<String, Object> p = r.get(0);
    Long proId = ((Number) p.get("professional_id")).longValue();
    if (proId.equals(me.getId())) return bad("Este pacote é seu.");
    List<UUID> pending = jdbc.queryForList("SELECT id FROM package_purchase_ WHERE package_id = ? AND user_id = ? AND status = 'PENDING'", UUID.class, id, me.getId());
    UUID pid;
    if (!pending.isEmpty()) {
      pid = pending.get(0);
    } else {
      pid = UUID.randomUUID();
      BigDecimal price = (BigDecimal) p.get("price");
      BigDecimal total = Pricing.clientTotal(price);
      Map<String, String> ch = asaasService.createExtraCharge(me, total, "Pacote Opus: " + p.get("title"));
      jdbc.update("INSERT INTO package_purchase_ (id, package_id, user_id, professional_id, sessions_total, service_value, client_total, status, pix_payment_id, pix_copy_paste, pix_qr, created_at) " +
              "VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)",
          pid, id, me.getId(), proId, p.get("sessions"), price, total, ch.get("id"), ch.get("payload"), ch.get("encodedImage"), Timestamp.valueOf(LocalDateTime.now()));
    }
    Map<String, Object> out = purchase(pid);
    out.put("testMode", asaasService.isTestMode());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/package-purchases/{id}/simulate")
  public ResponseEntity<?> simulatePackage(@PathVariable UUID id) {
    if (!asaasService.isTestMode()) return bad("O pagamento de teste está desligado.");
    User me = userService.findAuthUser();
    if (jdbc.queryForObject("SELECT COUNT(*) FROM package_purchase_ WHERE id = ? AND user_id = ?", Long.class, id, me.getId()) == 0) return bad("Compra não encontrada.");
    markPackagePaid(id);
    return ResponseEntity.ok(purchase(id));
  }

  @GetMapping("/packages/mine")
  public List<Map<String, Object>> myPackages() {
    User me = userService.findAuthUser();
    return jdbc.queryForList("SELECT pp.id, pp.professional_id AS \"professionalId\", u.name AS \"professionalName\", lp.title, pp.sessions_total AS \"sessionsTotal\", " +
        "pp.sessions_used AS \"sessionsUsed\", pp.client_total AS \"clientTotal\", pp.status, pp.pix_copy_paste AS \"pixCopyPaste\", pp.pix_qr AS \"pixQrCode\", pp.created_at AS \"createdAt\" " +
        "FROM package_purchase_ pp JOIN lesson_package_ lp ON lp.id = pp.package_id JOIN user_ u ON u.id = pp.professional_id " +
        "WHERE pp.user_id = ? AND pp.status IN ('PENDING','PAID') ORDER BY pp.created_at DESC", me.getId());
  }

  /** Paga o pedido com uma aula do pacote (o valor já está no cofre). */
  @PostMapping("/orders/{id}/use-package")
  public ResponseEntity<?> usePackage(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || o.getCustomer() == null || !o.getCustomer().getId().equals(me.getId())) return bad("Pedido não encontrado.");
    if (o.getProfessional() == null || Boolean.TRUE.equals(o.getPixPaid())) return bad("Este pedido não pode usar pacote agora.");
    List<Map<String, Object>> r = jdbc.queryForList("SELECT id, sessions_total, sessions_used, service_value FROM package_purchase_ " +
        "WHERE user_id = ? AND professional_id = ? AND status = 'PAID' AND sessions_used < sessions_total ORDER BY created_at LIMIT 1", me.getId(), o.getProfessional().getId());
    if (r.isEmpty()) return bad("Você não tem aulas sobrando em pacote com este profissional.");
    Map<String, Object> p = r.get(0);
    UUID pid = (UUID) p.get("id");
    BigDecimal unit = ((BigDecimal) p.get("service_value")).divide(BigDecimal.valueOf(((Number) p.get("sessions_total")).longValue()), 2, RoundingMode.HALF_UP);
    int n = jdbc.update("UPDATE package_purchase_ SET sessions_used = sessions_used + 1 WHERE id = ? AND sessions_used < sessions_total", pid);
    if (n == 0) return bad("O pacote acabou.");
    o.setServiceValue(unit);
    o.setTotalCost(Pricing.clientTotal(unit));
    o.setDiscountAmount(BigDecimal.ZERO);
    o.setPixPaid(true);
    o.setStatus(Status.AWAITING_PAYMENT);
    orderRepository.save(o);
    jdbc.update("UPDATE order_ SET package_purchase_id = ? WHERE id = ?", pid, id);
    transactionRepository.save(Transaction.builder()
        .paymentStatus(PaymentStatus.PAID).paymentType(PaymentType.PIX).asaasPaymentId("PKG-" + pid + "-" + id)
        .totalPaid(o.getTotalCost()).creditCardValue(BigDecimal.ZERO).customerBalanceValue(BigDecimal.ZERO)
        .appTaxPercentage(Pricing.FEE_RATE.multiply(BigDecimal.valueOf(100))).appTaxValue(o.getTotalCost().subtract(unit))
        .professionalValue(unit).processingCost(BigDecimal.ZERO).paymentMdrTax(BigDecimal.ZERO).paymentMdrValue(BigDecimal.ZERO)
        .paymentTotalCost(BigDecimal.ZERO).appProfit(BigDecimal.ZERO).order(o).customer(me).billingOrderDate(LocalDate.now())
        .orderIsDone(false).professionalIsPaid(false).build());
    notificationService.sendToUser(o.getProfessional(), true, firstName(me) + " usou uma aula do pacote. O pedido está pago — pode ir!");
    return ResponseEntity.ok(Map.of("status", "AWAITING_PAYMENT", "remaining", ((Number) p.get("sessions_total")).intValue() - ((Number) p.get("sessions_used")).intValue() - 1));
  }

  public boolean markPackagePaidByPixId(String pixId) {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM package_purchase_ WHERE pix_payment_id = ? AND status = 'PENDING'", UUID.class, pixId);
    if (ids.isEmpty()) return false;
    markPackagePaid(ids.get(0));
    return true;
  }

  private void markPackagePaid(UUID id) {
    int n = jdbc.update("UPDATE package_purchase_ SET status = 'PAID', paid_at = ? WHERE id = ? AND status = 'PENDING'", Timestamp.valueOf(LocalDateTime.now()), id);
    if (n == 0) return;
    Map<String, Object> p = jdbc.queryForMap("SELECT pp.professional_id, pp.sessions_total, u.name FROM package_purchase_ pp JOIN user_ u ON u.id = pp.user_id WHERE pp.id = ?", id);
    userRepository.findById(((Number) p.get("professional_id")).longValue()).ifPresent(pro ->
        notificationService.sendToUser(pro, true, firstName((String) p.get("name")) + " comprou um pacote de " + p.get("sessions_total") + " aulas com você!"));
  }

  private Map<String, Object> purchase(UUID id) {
    return new LinkedHashMap<>(jdbc.queryForMap("SELECT id, status, client_total AS \"clientTotal\", sessions_total AS \"sessionsTotal\", sessions_used AS \"sessionsUsed\", " +
        "pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\" FROM package_purchase_ WHERE id = ?", id));
  }

  /** Release de pacotes: o pagamento de cada aula é liberado pela conclusão normal do pedido. */

  // ───────────── Cupons ─────────────

  @PostMapping("/orders/{id}/coupon")
  public ResponseEntity<?> applyCoupon(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || o.getCustomer() == null || !o.getCustomer().getId().equals(me.getId())) return bad("Pedido não encontrado.");
    if (Boolean.TRUE.equals(o.getPixPaid())) return bad("Este pedido já foi pago.");
    if (o.getTotalCost() == null || o.getTotalCost().signum() <= 0) return bad("O valor do pedido ainda não foi definido.");
    if (o.getDiscountAmount() != null && o.getDiscountAmount().signum() > 0) return bad("Este pedido já tem um desconto.");
    String code = Objects.toString(b.get("code"), "").trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM coupon_ WHERE code = ? AND active = TRUE", code);
    if (r.isEmpty()) return bad("Cupom não encontrado ou encerrado.");
    Map<String, Object> c = r.get(0);
    if (c.get("valid_until") != null && LocalDate.now(BR).isAfter(((java.sql.Date) c.get("valid_until")).toLocalDate())) return bad("Este cupom venceu.");
    if (c.get("max_uses") != null && ((Number) c.get("used")).intValue() >= ((Number) c.get("max_uses")).intValue()) return bad("Este cupom já foi todo usado.");
    if (Boolean.TRUE.equals(c.get("one_per_user")) && jdbc.queryForObject("SELECT COUNT(*) FROM coupon_use_ WHERE code = ? AND user_id = ?", Long.class, code, me.getId()) > 0) return bad("Você já usou este cupom.");
    BigDecimal min = c.get("min_order") == null ? BigDecimal.ZERO : (BigDecimal) c.get("min_order");
    if (o.getTotalCost().compareTo(min) < 0) return bad("Este cupom vale para pedidos a partir de R$ " + min.setScale(2) + ".");
    BigDecimal disc = c.get("percent") != null && ((BigDecimal) c.get("percent")).signum() > 0
        ? o.getTotalCost().multiply((BigDecimal) c.get("percent")).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        : (BigDecimal) c.get("amount");
    BigDecimal fee = o.getTotalCost().subtract(o.getServiceValue() != null ? o.getServiceValue() : Pricing.serviceFromTotal(o.getTotalCost()));
    // O desconto sai da taxa OPUS e, se passar dela, fica no máximo em 90% do pedido (o profissional nunca perde)
    disc = disc.min(o.getTotalCost().multiply(new BigDecimal("0.9"))).setScale(2, RoundingMode.HALF_UP);
    o.setDiscountAmount(disc);
    orderRepository.save(o);
    jdbc.update("UPDATE order_ SET coupon_code = ? WHERE id = ?", code, id);
    jdbc.update("UPDATE coupon_ SET used = used + 1 WHERE code = ?", code);
    jdbc.update("INSERT INTO coupon_use_ (code, user_id, order_id, discount, used_at) VALUES (?, ?, ?, ?, ?)", code, me.getId(), id, disc, Timestamp.valueOf(LocalDateTime.now()));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", code);
    out.put("discount", disc);
    out.put("toPay", o.getTotalCost().subtract(disc));
    out.put("coveredByOpus", disc.min(fee));
    return ResponseEntity.ok(out);
  }

  // ───────────── Indique e ganhe ─────────────

  @GetMapping("/referral/me")
  public Map<String, Object> referral() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", me.getUsername());
    out.put("points", settings.getInt(SettingsService.REFERRAL_POINTS));
    out.put("invited", jdbc.queryForObject("SELECT COUNT(*) FROM referral_ WHERE referrer_id = ?", Long.class, me.getId()));
    out.put("rewarded", jdbc.queryForObject("SELECT COUNT(*) FROM referral_ WHERE referrer_id = ? AND rewarded_at IS NOT NULL", Long.class, me.getId()));
    List<Map<String, Object>> by = jdbc.queryForList("SELECT u.name FROM referral_ r JOIN user_ u ON u.id = r.referrer_id WHERE r.referred_id = ?", me.getId());
    out.put("referredBy", by.isEmpty() ? null : firstName((String) by.get(0).get("name")));
    out.put("canApply", by.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE customer_id = ? AND status IN ('DONE','REVIEWED')", Long.class, me.getId()) == 0);
    return out;
  }

  @PostMapping("/referral/apply")
  public ResponseEntity<?> applyReferral(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String code = Objects.toString(b.get("code"), "").trim();
    User ref = userRepository.findByUsername(code);
    if (ref == null) ref = userRepository.findByUsername(code.toLowerCase());
    if (ref == null) return bad("Código não encontrado. Confira com quem te indicou.");
    if (ref.getId().equals(me.getId())) return bad("Você não pode usar o seu próprio código.");
    if (jdbc.queryForObject("SELECT COUNT(*) FROM referral_ WHERE referred_id = ?", Long.class, me.getId()) > 0) return bad("Você já usou um código de indicação.");
    if (jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE customer_id = ? AND status IN ('DONE','REVIEWED')", Long.class, me.getId()) > 0) return bad("O código vale só antes do primeiro serviço.");
    jdbc.update("INSERT INTO referral_ (referred_id, referrer_id, created_at) VALUES (?, ?, ?)", me.getId(), ref.getId(), Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(referral());
  }

  /** A cada 15 minutos: dá os pontos de indicação depois do primeiro serviço concluído do indicado. */
  @Scheduled(fixedDelay = 900_000, initialDelay = 120_000)
  public void rewardReferrals() {
    try {
      int pts = settings.getInt(SettingsService.REFERRAL_POINTS);
      for (Map<String, Object> r : jdbc.queryForList("SELECT r.referred_id, r.referrer_id FROM referral_ r WHERE r.rewarded_at IS NULL AND EXISTS " +
          "(SELECT 1 FROM order_ o WHERE o.customer_id = r.referred_id AND o.status IN ('DONE','REVIEWED'))")) {
        Long referred = ((Number) r.get("referred_id")).longValue(), referrer = ((Number) r.get("referrer_id")).longValue();
        int n = jdbc.update("UPDATE referral_ SET rewarded_at = ? WHERE referred_id = ? AND rewarded_at IS NULL", Timestamp.valueOf(LocalDateTime.now()), referred);
        if (n == 0) continue;
        User a = userRepository.findById(referrer).orElse(null), b = userRepository.findById(referred).orElse(null);
        if (a != null) {
          pointsRepository.save(Points.builder().user(a).amount(pts).reason("Indicação de " + firstName(b)).build());
          notificationService.sendToUser(a, false, firstName(b) + " fez o primeiro serviço com o seu código. Você ganhou " + pts + " pontos!");
        }
        if (b != null) {
          pointsRepository.save(Points.builder().user(b).amount(pts).reason("Bônus de boas-vindas (indicação)").build());
          notificationService.sendToUser(b, false, "Bônus de indicação: você ganhou " + pts + " pontos!");
        }
      }
    } catch (Exception e) {
      log.warn("Rotina de indicação falhou: {}", e.getMessage());
    }
  }

  // ───────────── Selo Top OPUS ─────────────

  @GetMapping("/professionals/{proId}/badges")
  public Map<String, Object> badges(@PathVariable Long proId) {
    long done = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", proId);
    BigDecimal rating = jdbc.queryForObject("SELECT ROUND(AVG(rating)::numeric, 1) FROM review_ WHERE professional_id = ? AND rating IS NOT NULL", BigDecimal.class, proId);
    long disputes = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND dispute_status IN ('OPEN','RESOLVED_CLIENT')", proId);
    long arrived = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND arrived_at IS NOT NULL AND date IS NOT NULL AND start_time IS NOT NULL", proId);
    long onTime = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND arrived_at IS NOT NULL AND date IS NOT NULL AND start_time IS NOT NULL " +
        "AND arrived_at <= (date + start_time + INTERVAL '10 minutes')", proId);
    Integer punctuality = arrived < 3 ? null : (int) Math.round(100.0 * onTime / arrived);
    boolean top = done >= 10 && rating != null && rating.compareTo(new BigDecimal("4.7")) >= 0
        && (punctuality == null || punctuality >= 90) && disputes * 20 <= done;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("top", top);
    out.put("done", done);
    out.put("rating", rating);
    out.put("punctuality", punctuality);
    out.put("disputes", disputes);
    out.put("criteria", List.of("10 serviços ou mais", "nota 4,7 ou mais", "chega no horário em 90% das vezes", "quase nenhuma disputa"));
    return out;
  }

  // ───────────── Lembretes ─────────────

  /** A cada 10 minutos: lembra cliente e profissional 24 h e 1 h antes. */
  @Scheduled(fixedDelay = 600_000, initialDelay = 150_000)
  public void reminders() {
    try {
      LocalDateTime now = LocalDateTime.now(BR);
      remind(now.plusHours(23), now.plusHours(25), "reminded_24h", "amanhã");
      remind(now, now.plusMinutes(70), "reminded_1h", "em cerca de 1 hora");
    } catch (Exception e) {
      log.warn("Rotina de lembretes falhou: {}", e.getMessage());
    }
  }

  private void remind(LocalDateTime from, LocalDateTime to, String column, String when) {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM order_ WHERE status IN ('CONFIRMED','AWAITING_PAYMENT') AND COALESCE(" + column + ", FALSE) = FALSE " +
        "AND date IS NOT NULL AND start_time IS NOT NULL AND (date + start_time) BETWEEN ? AND ?", UUID.class, Timestamp.valueOf(from), Timestamp.valueOf(to));
    for (UUID id : ids) {
      jdbc.update("UPDATE order_ SET " + column + " = TRUE WHERE id = ?", id);
      orderRepository.findById(id).ifPresent(o -> {
        String hora = o.getStartTime() != null ? o.getStartTime().toString().substring(0, 5) : "";
        String svc = o.getProduct() != null ? o.getProduct().getName() : "serviço";
        if (o.getCustomer() != null) notificationService.sendToUser(o.getCustomer(), false,
            "Lembrete: seu " + svc + " com " + firstName(o.getProfessional()) + " é " + when + (hora.isEmpty() ? "" : ", às " + hora) + ".");
        if (o.getProfessional() != null) notificationService.sendToUser(o.getProfessional(), true,
            "Lembrete: atendimento de " + firstName(o.getCustomer()) + " " + when + (hora.isEmpty() ? "" : ", às " + hora) + ". Não esqueça de tocar em \"Saí para o atendimento\".");
      });
    }
  }

  // ───────────── utilitários ─────────────

  private BigDecimal hourlyRate(Long proId) {
    try {
      return jdbc.queryForObject("SELECT p.hourly_rate FROM user_ u JOIN professional_ p ON p.id = u.professional_id WHERE u.id = ?", BigDecimal.class, proId);
    } catch (Exception e) {
      return null;
    }
  }

  private long n(String sql, Object... args) {
    Long v = jdbc.queryForObject(sql, Long.class, args);
    return v == null ? 0 : v;
  }

  private static int intOf(Object o, int def) {
    try { return o == null ? def : (int) Math.round(Double.parseDouble(o.toString())); } catch (Exception e) { return def; }
  }
}
