package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Points;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.PointsRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.OrderService;
import br.com.personalfighters.service.SettingsService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/** Painel administrativo completo (Início, Pedidos, Usuários, Financeiro, Avisos, Configurações, Equipe...). */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

  private static final String PAID = "o.pix_paid = TRUE";

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final OrderService orderService;
  private final PointsRepository pointsRepository;
  private final NotificationService notificationService;
  private final SettingsService settings;

  // ───────────── Início ─────────────

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(@RequestParam(defaultValue = "week") String range) {
    LocalDateTime from = start(range);
    LocalDateTime prevFrom = from.minus(Duration.between(from, LocalDateTime.now()));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("range", range);
    out.put("orders", count("SELECT COUNT(*) FROM order_ WHERE created_at >= ?", ts(from)));
    out.put("ordersPrev", count("SELECT COUNT(*) FROM order_ WHERE created_at >= ? AND created_at < ?", ts(prevFrom), ts(from)));
    out.put("gmv", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ?", ts(from)));
    out.put("gmvPrev", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ? AND o.created_at < ?", ts(prevFrom), ts(from)));
    out.put("fee", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ? AND o.status <> 'CANCELLED'", ts(from)));
    out.put("feePrev", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ? AND o.created_at < ? AND o.status <> 'CANCELLED'", ts(prevFrom), ts(from)));
    out.put("escrow", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    out.put("escrowOrders", count("SELECT COUNT(*) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    out.put("newCustomers", count("SELECT COUNT(DISTINCT u.id) FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'CUSTOMER' AND u.created_at_ >= ?", ts(from)));
    out.put("newProfessionals", count("SELECT COUNT(DISTINCT u.id) FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'PROFESSIONAL' AND u.created_at_ >= ?", ts(from)));
    long done = count("SELECT COUNT(*) FROM order_ WHERE status IN ('DONE','REVIEWED')");
    long disputed = count("SELECT COUNT(*) FROM order_ WHERE dispute_status IS NOT NULL");
    out.put("cleanRate", done + disputed == 0 ? 100 : Math.round(100.0 * done / (done + disputed)));
    Map<String, Object> alerts = new LinkedHashMap<>();
    alerts.put("verifications", count("SELECT COUNT(*) FROM identity_verification_ WHERE status = 'PENDING'"));
    alerts.put("disputes", count("SELECT COUNT(*) FROM order_ WHERE dispute_status = 'OPEN'"));
    alerts.put("support", count("SELECT COUNT(DISTINCT user_id) FROM support_message_ WHERE from_admin = FALSE AND read_by_admin = FALSE"));
    alerts.put("sos", count("SELECT COUNT(*) FROM protection_session_ WHERE active = TRUE AND level >= 3 AND acknowledged_at IS NULL"));
    out.put("alerts", alerts);
    int days = "month".equals(range) ? 30 : 7;
    List<Map<String, Object>> series = new ArrayList<>();
    for (int i = days - 1; i >= 0; i--) {
      LocalDate d = LocalDate.now().minusDays(i);
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("date", d);
      p.put("fee", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID +
          " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(d.atStartOfDay()), ts(d.plusDays(1).atStartOfDay())));
      series.add(p);
    }
    out.put("series", series);
    out.put("topProfessionals", jdbc.queryForList(
        "SELECT u.id, u.name, COUNT(o.id) AS done, (SELECT ROUND(AVG(r.rating)::numeric, 1) FROM review_ r WHERE r.professional_id = u.id AND r.rating IS NOT NULL) AS rating " +
            "FROM order_ o JOIN user_ u ON u.id = o.professional_id WHERE o.status IN ('DONE','REVIEWED') GROUP BY u.id, u.name ORDER BY done DESC LIMIT 5"));
    out.put("topServices", jdbc.queryForList(
        "SELECT COALESCE(p.name, 'Outros') AS name, COUNT(*) AS n FROM order_ o LEFT JOIN product_ p ON p.id = o.product_id WHERE o.created_at >= ? GROUP BY COALESCE(p.name, 'Outros') ORDER BY n DESC LIMIT 5",
        ts(LocalDateTime.now().minusDays(30))));
    return out;
  }

  // ───────────── Pedidos ─────────────

  @GetMapping("/orders")
  public Map<String, Object> orders(@RequestParam(defaultValue = "ALL") String status, @RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "0") int page) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE 1=1");
    switch (status) {
      case "ALL": break;
      case "DISPUTE": where.append(" AND o.dispute_status = 'OPEN'"); break;
      case "ESCROW": where.append(" AND o.pix_paid = TRUE AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"); break;
      case "DONE": where.append(" AND o.status IN ('DONE','REVIEWED')"); break;
      default: where.append(" AND o.status = ?"); args.add(status);
    }
    if (!q.isBlank()) {
      where.append(" AND (LOWER(c.name) LIKE ? OR LOWER(p.name) LIKE ? OR CAST(o.id AS TEXT) LIKE ?)");
      String like = "%" + q.toLowerCase().trim() + "%";
      args.add(like); args.add(like); args.add(like);
    }
    String base = " FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id LEFT JOIN user_ p ON p.id = o.professional_id LEFT JOIN product_ pr ON pr.id = o.product_id " + where;
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(page * 50);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("rows", jdbc.queryForList("SELECT o.id, o.created_at AS \"createdAt\", o.date, o.start_time AS \"startTime\", o.status, o.total_cost AS \"totalCost\", " +
        "o.service_value AS \"serviceValue\", o.pix_paid AS \"pixPaid\", o.pix_released AS \"pixReleased\", o.dispute_status AS \"disputeStatus\", " +
        "c.name AS \"customerName\", p.name AS \"professionalName\", pr.name AS \"product\", o.quote_group AS \"quoteGroup\", o.disputed_at AS \"disputedAt\", " +
        "(SELECT COUNT(*) FROM order_stage_ s WHERE s.order_id = o.id) AS \"stages\", (SELECT COUNT(*) FROM order_stage_ s WHERE s.order_id = o.id AND s.status = 'APPROVED') AS \"stagesApproved\", " +
        "(SELECT COUNT(*) FROM order_stage_ s WHERE s.order_id = o.id AND s.status = 'DONE') AS \"stagesDone\", " +
        "(SELECT COUNT(*) FROM protection_session_ ps WHERE ps.order_id = o.id AND ps.active = TRUE AND ps.level >= 3) AS \"sos\"" + base + " ORDER BY o.created_at DESC NULLS LAST LIMIT 50 OFFSET ?", pageArgs.toArray()));
    out.put("total", jdbc.queryForObject("SELECT COUNT(*)" + base, Long.class, args.toArray()));
    Map<String, Object> counts = new LinkedHashMap<>();
    for (Map<String, Object> r : jdbc.queryForList("SELECT status, COUNT(*) AS n FROM order_ GROUP BY status")) counts.put(String.valueOf(r.get("status")), r.get("n"));
    counts.put("ESCROW", count("SELECT COUNT(*) FROM order_ o WHERE o.pix_paid = TRUE AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    counts.put("DISPUTE", count("SELECT COUNT(*) FROM order_ WHERE dispute_status = 'OPEN'"));
    counts.put("ALL", count("SELECT COUNT(*) FROM order_"));
    out.put("counts", counts);
    Map<String, Object> k = new LinkedHashMap<>();
    k.put("escrow", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    k.put("escrowOrders", counts.get("ESCROW"));
    k.put("toApprove", count("SELECT COUNT(*) FROM order_ WHERE status = 'PENDING_CLIENT_CONFIRMATION'"));
    k.put("toApproveValue", money("SELECT COALESCE(SUM(COALESCE(service_value, total_cost/1.05)),0) FROM order_ WHERE status = 'PENDING_CLIENT_CONFIRMATION'"));
    k.put("disputes", counts.get("DISPUTE"));
    List<Map<String, Object>> nd = jdbc.queryForList("SELECT MIN(disputed_at) AS d FROM order_ WHERE dispute_status = 'OPEN'");
    Object first = nd.isEmpty() ? null : nd.get(0).get("d");
    if (first instanceof java.sql.Timestamp) k.put("nextDisputeDeadline", ((java.sql.Timestamp) first).toLocalDateTime().plusHours(Math.max(1, settings.getInt(SettingsService.DISPUTE_TIMEOUT_HOURS))));
    k.put("stagesWaiting", count("SELECT COUNT(*) FROM order_stage_ WHERE status = 'DONE'"));
    k.put("stagesOrders", count("SELECT COUNT(DISTINCT order_id) FROM order_stage_ WHERE status = 'DONE'"));
    out.put("kpis", k);
    return out;
  }

  @PostMapping("/orders/{id}/cancel")
  public ResponseEntity<?> cancelOrder(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null) return bad("Pedido não encontrado.");
    String note = Objects.toString(body.get("note"), "").trim();
    if (note.length() < 5) return bad("Escreva o motivo.");
    orderService.cancelOrder(o, Role.ADMIN);
    jdbc.update("UPDATE order_ SET cancelled_by = 'ADMIN', cancel_reason = ? WHERE id = ?", note, id);
    settings.audit(admin, "ORDER_CANCEL", id.toString(), note);
    return ResponseEntity.ok(Map.of("status", "CANCELLED"));
  }

  // ───────────── Usuários ─────────────

  @GetMapping("/users")
  public List<Map<String, Object>> users(@RequestParam(defaultValue = "ALL") String type, @RequestParam(defaultValue = "") String q) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder("WHERE 1=1");
    if ("CUSTOMER".equals(type) || "PROFESSIONAL".equals(type) || "ADMIN".equals(type)) {
      where.append(" AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = ?)");
      args.add(type);
    }
    if ("BLOCKED".equals(type)) where.append(" AND COALESCE(u.blocked, FALSE) = TRUE");
    if (!q.isBlank()) {
      where.append(" AND (LOWER(u.name) LIKE ? OR LOWER(u.username) LIKE ? OR LOWER(u.email) LIKE ?)");
      String like = "%" + q.toLowerCase().trim() + "%";
      args.add(like); args.add(like); args.add(like);
    }
    return jdbc.queryForList("SELECT u.id, u.name, u.username, u.email, u.verified, COALESCE(u.blocked,FALSE) AS blocked, u.created_at_ AS \"createdAt\", " +
        "(SELECT STRING_AGG(r.role, ',') FROM user_role r WHERE r.user_id = u.id) AS roles, " +
        "(SELECT COUNT(*) FROM order_ o WHERE (o.customer_id = u.id OR o.professional_id = u.id) AND o.status IN ('DONE','REVIEWED')) AS done, " +
        "(SELECT ROUND(AVG(rv.rating)::numeric, 1) FROM review_ rv WHERE rv.professional_id = u.id AND rv.rating IS NOT NULL) AS rating " +
        "FROM user_ u " + where + " ORDER BY u.id DESC LIMIT 200", args.toArray());
  }

  @GetMapping("/users/kpis")
  public Map<String, Object> usersKpis() {
    Map<String, Object> k = new LinkedHashMap<>();
    LocalDateTime week = LocalDateTime.now().minusDays(7);
    k.put("customers", count("SELECT COUNT(DISTINCT user_id) FROM user_role WHERE role = 'CUSTOMER'"));
    k.put("customersWeek", count("SELECT COUNT(DISTINCT u.id) FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'CUSTOMER' AND u.created_at_ >= ?", ts(week)));
    k.put("pros", count("SELECT COUNT(DISTINCT user_id) FROM user_role WHERE role = 'PROFESSIONAL'"));
    k.put("prosOnline", count("SELECT COUNT(*) FROM professional_ WHERE COALESCE(is_online, FALSE) = TRUE"));
    k.put("pending", count("SELECT COUNT(*) FROM identity_verification_ WHERE status = 'PENDING'"));
    List<Map<String, Object>> old = jdbc.queryForList("SELECT MIN(submitted_at) AS d FROM identity_verification_ WHERE status = 'PENDING'");
    k.put("oldestPending", old.isEmpty() ? null : old.get(0).get("d"));
    k.put("blocked", count("SELECT COUNT(*) FROM user_ WHERE COALESCE(blocked, FALSE) = TRUE"));
    return k;
  }

  @GetMapping("/users/{id}")
  public ResponseEntity<?> user(@PathVariable Long id) {
    User u = userRepository.findById(id).orElse(null);
    if (u == null) return bad("Usuário não encontrado.");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", u.getId());
    out.put("name", u.getName());
    out.put("username", u.getUsername());
    out.put("email", u.getEmail());
    out.put("phone", u.getPhoneNumber());
    out.put("verified", u.getVerified());
    out.put("roles", u.getRole());
    out.put("avatarUrl", u.getAvatarUrl());
    out.putAll(jdbc.queryForMap("SELECT COALESCE(blocked,FALSE) AS blocked, blocked_reason AS \"blockedReason\", created_at_ AS \"createdAt\" FROM user_ WHERE id = ?", id));
    out.put("stats", stats(id));
    out.put("pixKey", br.com.personalfighters.service.WalletService.mask(u.getPixKey()));
    out.put("verificationStatus", u.getVerificationStatus());
    out.put("earnedMonth", money("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND created_at >= ?", id, ts(LocalDate.now().withDayOfMonth(1).atStartOfDay())));
    out.put("earnedTotal", money("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE')", id));
    out.put("acceptRate", jdbc.queryForObject("SELECT CASE WHEN COUNT(*) = 0 THEN NULL ELSE ROUND(100.0 * SUM(CASE WHEN status NOT IN ('BLOCKED','OPEN') THEN 1 ELSE 0 END) / COUNT(*)) END FROM order_ WHERE professional_id = ?", Long.class, id));
    List<Map<String, Object>> ver = jdbc.queryForList("SELECT id, status, submitted_at AS \"submittedAt\", reviewed_at AS \"reviewedAt\", reviewed_by AS \"reviewedBy\" FROM identity_verification_ WHERE user_id = ? ORDER BY submitted_at DESC NULLS LAST LIMIT 1", id);
    out.put("verification", ver.isEmpty() ? null : ver.get(0));
    out.put("points", count("SELECT COALESCE(SUM(amount),0) FROM points_ WHERE user_id = ?", id));
    out.put("orders", jdbc.queryForList("SELECT o.id, o.date, o.status, o.total_cost AS \"totalCost\", o.dispute_status AS \"disputeStatus\", " +
        "CASE WHEN o.customer_id = ? THEN 'cliente' ELSE 'profissional' END AS papel FROM order_ o WHERE o.customer_id = ? OR o.professional_id = ? ORDER BY o.created_at DESC NULLS LAST LIMIT 20", id, id, id));
    out.put("blockedAttempts", jdbc.queryForList("SELECT order_id AS \"orderId\", matched_pattern AS pattern, original_text AS text, blocked_at AS \"at\" FROM blocked_attempt_ WHERE sender_id = ? ORDER BY blocked_at DESC LIMIT 20", id));
    return ResponseEntity.ok(out);
  }

  @PostMapping("/users/{id}/block")
  @Transactional
  public ResponseEntity<?> block(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    if (admin.getId().equals(id)) return bad("Você não pode bloquear a sua própria conta.");
    String reason = Objects.toString(body.get("reason"), "").trim();
    if (reason.length() < 5) return bad("Escreva o motivo do bloqueio.");
    jdbc.update("UPDATE user_ SET blocked = TRUE, blocked_reason = ?, active = FALSE WHERE id = ?", reason, id);
    settings.audit(admin, "USER_BLOCK", String.valueOf(id), reason);
    return ResponseEntity.ok(Map.of("blocked", true));
  }

  @PostMapping("/users/{id}/unblock")
  public ResponseEntity<?> unblock(@PathVariable Long id) {
    User admin = userService.findAuthUser();
    jdbc.update("UPDATE user_ SET blocked = FALSE, blocked_reason = NULL, active = TRUE WHERE id = ?", id);
    settings.audit(admin, "USER_UNBLOCK", String.valueOf(id), null);
    return ResponseEntity.ok(Map.of("blocked", false));
  }

  @PostMapping("/users/{id}/points")
  public ResponseEntity<?> givePoints(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    User u = userRepository.findById(id).orElse(null);
    if (u == null) return bad("Usuário não encontrado.");
    int amount;
    try { amount = Integer.parseInt(Objects.toString(body.get("amount"), "0")); } catch (Exception e) { return bad("Quantidade inválida."); }
    if (amount == 0 || Math.abs(amount) > 5000) return bad("Use de 1 a 5000 pontos.");
    String reason = Objects.toString(body.get("reason"), "Presente da equipe OPUS").trim();
    pointsRepository.save(Points.builder().user(u).amount(amount).reason(reason).build());
    settings.audit(admin, "USER_POINTS", String.valueOf(id), amount + " · " + reason);
    if (amount > 0) notificationService.sendToUser(u, SocialController.hasRole(u, Role.PROFESSIONAL), "Você ganhou " + amount + " pontos: " + reason);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/users/{id}/message")
  public ResponseEntity<?> message(@PathVariable Long id, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    User u = userRepository.findById(id).orElse(null);
    if (u == null) return bad("Usuário não encontrado.");
    String text = Objects.toString(body.get("text"), "").trim();
    if (text.isEmpty()) return bad("Escreva a mensagem.");
    jdbc.update("INSERT INTO support_message_ (user_id, from_admin, admin_name, text, created_at, read_by_user, read_by_admin) VALUES (?, TRUE, ?, ?, ?, FALSE, TRUE)",
        id, "Equipe OPUS · " + firstName(admin), text, ts(LocalDateTime.now()));
    notificationService.sendToUser(u, SocialController.hasRole(u, Role.PROFESSIONAL), "Equipe OPUS: " + (text.length() > 80 ? text.substring(0, 80) + "…" : text));
    settings.audit(admin, "USER_MESSAGE", String.valueOf(id), text);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  /** Números de um usuário (como profissional e como cliente). */
  Map<String, Object> stats(Long id) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("servicesDone", count("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id));
    s.put("ordersAsClient", count("SELECT COUNT(*) FROM order_ WHERE customer_id = ?", id));
    s.put("rating", jdbc.queryForObject("SELECT ROUND(AVG(rating)::numeric, 1) FROM review_ WHERE professional_id = ? AND rating IS NOT NULL", BigDecimal.class, id));
    s.put("disputes", count("SELECT COUNT(*) FROM order_ WHERE (professional_id = ? OR customer_id = ?) AND dispute_status IS NOT NULL", id, id));
    s.put("cancelsAsClient", count("SELECT COUNT(*) FROM order_ WHERE customer_id = ? AND status = 'CANCELLED'", id));
    long arrived = count("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND arrived_at IS NOT NULL AND date IS NOT NULL AND start_time IS NOT NULL", id);
    long onTime = count("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND arrived_at IS NOT NULL AND date IS NOT NULL AND start_time IS NOT NULL " +
        "AND arrived_at <= (date + start_time + INTERVAL '10 minutes')", id);
    s.put("punctuality", arrived == 0 ? null : Math.round(100.0 * onTime / arrived));
    return s;
  }

  // ───────────── Financeiro ─────────────

  @GetMapping("/finance")
  public Map<String, Object> finance() {
    Map<String, Object> out = new LinkedHashMap<>();
    LocalDateTime month = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    out.put("escrow", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    out.put("escrowExtras", money("SELECT COALESCE(SUM(client_total),0) FROM order_extra_ WHERE status = 'PAID' AND released = FALSE"));
    out.put("escrowClasses", money("SELECT COALESCE(SUM(client_total),0) FROM class_enrollment_ WHERE status = 'PAID' AND released = FALSE AND refunded = FALSE"));
    out.put("released", money("SELECT COALESCE(SUM(COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE o.pix_released = TRUE AND o.payment_released_at >= ?", ts(month)));
    out.put("fee", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ?", ts(month)));
    out.put("feeExtras", money("SELECT COALESCE(SUM(client_total - service_value),0) FROM order_extra_ WHERE status = 'PAID' AND paid_at >= ?", ts(month)));
    out.put("refunded", money("SELECT COALESCE(SUM(client_refund),0) FROM order_ WHERE client_refund IS NOT NULL AND created_at >= ?", ts(month)));
    out.put("cancelFees", money("SELECT COALESCE(SUM(cancel_fee),0) FROM order_ WHERE cancel_fee IS NOT NULL AND created_at >= ?", ts(month)));
    out.put("pointsDiscount", money("SELECT COALESCE(SUM(discount_amount),0) FROM order_ WHERE discount_amount > 0 AND pix_paid = TRUE AND created_at >= ?", ts(month)));
    long pts = count("SELECT COALESCE(SUM(amount),0) FROM points_");
    out.put("pointsOutstanding", pts);
    out.put("pointsReserve", BigDecimal.valueOf(pts).divide(BigDecimal.TEN));
    List<Map<String, Object>> monthly = new ArrayList<>();
    for (int i = 5; i >= 0; i--) {
      LocalDate m = LocalDate.now().withDayOfMonth(1).minusMonths(i);
      Map<String, Object> p = new LinkedHashMap<>();
      p.put("month", m.toString().substring(0, 7));
      p.put("fee", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID +
          " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(m.atStartOfDay()), ts(m.plusMonths(1).atStartOfDay())));
      monthly.add(p);
    }
    out.put("monthly", monthly);
    out.put("movements", movements(60));
    out.put("gmv", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0)),0) FROM order_ o WHERE " + PAID + " AND o.created_at >= ?", ts(month)));
    out.put("paidOrders", count("SELECT COUNT(*) FROM order_ o WHERE " + PAID + " AND o.created_at >= ?", ts(month)));
    out.put("escrowOrders", count("SELECT COUNT(*) FROM order_ o WHERE " + PAID + " AND COALESCE(o.pix_released,FALSE) = FALSE AND o.status NOT IN ('CANCELLED')"));
    out.put("withdrawn", money("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE kind = 'WITHDRAW' AND status = 'DONE' AND created_at >= ?", ts(month)));
    out.put("withdrawFailed", count("SELECT COUNT(*) FROM wallet_entry_ WHERE kind = 'WITHDRAW' AND status = 'FAILED' AND created_at >= ?", ts(month)));
    out.put("stagesReleased", money("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE kind = 'STAGE' AND created_at >= ?", ts(month)));
    out.put("wallets", money("SELECT COALESCE(SUM(CASE WHEN kind IN ('CREDIT','STAGE') THEN amount WHEN status = 'DONE' THEN -amount ELSE 0 END),0) FROM wallet_entry_ WHERE status IN ('DONE','PENDING')"));
    out.put("boosts", money("SELECT COALESCE(SUM(price),0) FROM pro_boost_ WHERE status IN ('PAID','ACTIVE') AND created_at >= ?", ts(month)));
    out.put("prevFee", money("SELECT COALESCE(SUM(o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)),0) FROM order_ o WHERE " + PAID + " AND o.status <> 'CANCELLED' AND o.created_at >= ? AND o.created_at < ?", ts(month.minusMonths(1)), ts(month)));
    out.put("wallet", jdbc.queryForList("SELECT w.created_at AS at, w.order_id AS \"orderId\", w.kind, w.status, w.amount AS value, u.name AS person FROM wallet_entry_ w LEFT JOIN user_ u ON u.id = w.professional_id ORDER BY w.created_at DESC LIMIT 40"));
    return out;
  }

  @GetMapping(value = "/finance/export.csv")
  public ResponseEntity<byte[]> exportCsv() {
    StringBuilder sb = new StringBuilder("﻿quando;pedido;movimento;pessoa;valor\n");
    for (Map<String, Object> m : movements(5000)) {
      sb.append(m.get("at")).append(';').append(m.get("orderId")).append(';').append(m.get("kind")).append(';')
          .append(Objects.toString(m.get("person"), "").replace(';', ',')).append(';')
          .append(Objects.toString(m.get("value"), "").replace('.', ',')).append('\n');
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=opus-financeiro.csv")
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .body(sb.toString().getBytes(StandardCharsets.UTF_8));
  }

  private List<Map<String, Object>> movements(int limit) {
    return jdbc.queryForList(
        "SELECT * FROM (" +
            "SELECT o.created_at AS at, o.id AS \"orderId\", 'Pagamento recebido' AS kind, c.name AS person, (o.total_cost - COALESCE(o.discount_amount,0)) AS value, 'IN' AS tipo FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id WHERE o.pix_paid = TRUE " +
            "UNION ALL SELECT o.payment_released_at, o.id, 'Liberado ao profissional', p.name, COALESCE(o.service_value, o.total_cost/1.05), 'OUT' FROM order_ o LEFT JOIN user_ p ON p.id = o.professional_id WHERE o.pix_released = TRUE AND o.payment_released_at IS NOT NULL " +
            "UNION ALL SELECT o.payment_released_at, o.id, 'Taxa OPUS', NULL, (o.total_cost - COALESCE(o.discount_amount,0) - COALESCE(o.service_value, o.total_cost/1.05)), 'FEE' FROM order_ o WHERE o.pix_released = TRUE AND o.payment_released_at IS NOT NULL " +
            "UNION ALL SELECT o.created_at, o.id, 'Devolvido ao cliente', c.name, o.client_refund, 'REFUND' FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id WHERE o.client_refund IS NOT NULL AND o.client_refund > 0 " +
            "UNION ALL SELECT o.created_at, o.id, 'Taxa de deslocamento', p.name, o.cancel_fee, 'OUT' FROM order_ o LEFT JOIN user_ p ON p.id = o.professional_id WHERE o.cancel_fee IS NOT NULL AND o.cancel_fee > 0 " +
            "UNION ALL SELECT e.paid_at, e.order_id, 'Orçamento extra pago', NULL, e.client_total, 'IN' FROM order_extra_ e WHERE e.status = 'PAID' " +
            "UNION ALL SELECT ce.paid_at, ce.class_id, 'Aula em grupo paga', u.name, ce.client_total, 'IN' FROM class_enrollment_ ce LEFT JOIN user_ u ON u.id = ce.user_id WHERE ce.paid_at IS NOT NULL " +
            "UNION ALL SELECT pp.paid_at, pp.id, 'Pacote de aulas pago', u.name, pp.client_total, 'IN' FROM package_purchase_ pp LEFT JOIN user_ u ON u.id = pp.user_id WHERE pp.paid_at IS NOT NULL" +
            ") m WHERE at IS NOT NULL ORDER BY at DESC LIMIT " + Math.max(1, Math.min(limit, 5000)));
  }

  // ───────────── Avisos em massa ─────────────

  @PostMapping("/broadcast")
  public ResponseEntity<?> broadcast(@RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    String audience = Objects.toString(body.get("audience"), "ALL");
    String text = Objects.toString(body.get("text"), "").trim();
    if (text.length() < 5) return bad("Escreva o aviso.");
    if (text.length() > 180) return bad("O aviso pode ter no máximo 180 letras.");
    String roleFilter = "CUSTOMERS".equals(audience) ? "CUSTOMER" : "PROFESSIONALS".equals(audience) ? "PROFESSIONAL" : null;
    List<Long> ids = roleFilter == null
        ? jdbc.queryForList("SELECT id FROM user_ WHERE COALESCE(blocked,FALSE) = FALSE", Long.class)
        : jdbc.queryForList("SELECT DISTINCT u.id FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = ? AND COALESCE(u.blocked,FALSE) = FALSE", Long.class, roleFilter);
    int sent = 0;
    for (Long id : ids) {
      try {
        User u = userRepository.findById(id).orElse(null);
        if (u == null) continue;
        boolean pro = SocialController.hasRole(u, Role.PROFESSIONAL);
        if (roleFilter == null || "CUSTOMER".equals(roleFilter)) notificationService.sendToUser(u, false, text);
        if (pro && (roleFilter == null || "PROFESSIONAL".equals(roleFilter))) notificationService.sendToUser(u, true, text);
        sent++;
      } catch (Exception e) {
        log.warn("Aviso não enviado para {}: {}", id, e.getMessage());
      }
    }
    jdbc.update("INSERT INTO broadcast_ (audience, text, sent_count, admin_name, created_at) VALUES (?, ?, ?, ?, ?)", audience, text, sent, admin.getName(), ts(LocalDateTime.now()));
    settings.audit(admin, "BROADCAST", audience, text);
    return ResponseEntity.ok(Map.of("sent", sent));
  }

  @GetMapping("/broadcasts")
  public List<Map<String, Object>> broadcasts() {
    return jdbc.queryForList("SELECT id, audience, text, sent_count AS \"sentCount\", admin_name AS \"adminName\", created_at AS \"createdAt\" FROM broadcast_ ORDER BY id DESC LIMIT 50");
  }

  // ───────────── Configurações ─────────────

  @GetMapping("/settings")
  public Map<String, String> getSettings() {
    return settings.all();
  }

  @PutMapping("/settings")
  public ResponseEntity<?> putSettings(@RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    try {
      for (Map.Entry<String, Object> e : body.entrySet()) {
        if (!Objects.equals(settings.get(e.getKey()), Objects.toString(e.getValue(), ""))) settings.set(e.getKey(), Objects.toString(e.getValue(), ""), admin);
      }
    } catch (IllegalArgumentException e) {
      return bad(e.getMessage());
    }
    return ResponseEntity.ok(settings.all());
  }

  // ───────────── Equipe ─────────────

  // Equipe: veja PanelAuthController (acesso do painel separado do app)

  @GetMapping("/actions")
  public List<Map<String, Object>> actions() {
    return jdbc.queryForList("SELECT id, admin_name AS \"adminName\", action, target, detail, created_at AS \"createdAt\" FROM admin_action_ ORDER BY id DESC LIMIT 200");
  }

  // ───────────── Alertas de golpe ─────────────

  @GetMapping("/fraud")
  public Map<String, Object> fraud() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("contactAttempts", jdbc.queryForList(
        "SELECT b.sender_id AS \"userId\", u.name, COUNT(*) AS n, MAX(b.blocked_at) AS \"lastAt\" FROM blocked_attempt_ b JOIN user_ u ON u.id = b.sender_id GROUP BY b.sender_id, u.name HAVING COUNT(*) >= 2 ORDER BY n DESC LIMIT 50"));
    out.put("manyCancels", jdbc.queryForList(
        "SELECT u.id AS \"userId\", u.name, COUNT(*) AS n FROM order_ o JOIN user_ u ON u.id = o.customer_id WHERE o.status = 'CANCELLED' AND o.created_at >= ? GROUP BY u.id, u.name HAVING COUNT(*) >= 3 ORDER BY n DESC LIMIT 50",
        ts(LocalDateTime.now().minusDays(30))));
    out.put("manyDisputes", jdbc.queryForList(
        "SELECT u.id AS \"userId\", u.name, COUNT(*) AS n FROM order_ o JOIN user_ u ON u.id IN (o.customer_id, o.professional_id) WHERE o.dispute_status IS NOT NULL GROUP BY u.id, u.name HAVING COUNT(*) >= 2 ORDER BY n DESC LIMIT 50"));
    out.put("newAndBusy", jdbc.queryForList(
        "SELECT u.id AS \"userId\", u.name, COUNT(o.id) AS n FROM user_ u JOIN order_ o ON o.customer_id = u.id WHERE u.created_at_ >= ? GROUP BY u.id, u.name HAVING COUNT(o.id) >= 4 ORDER BY n DESC LIMIT 50",
        ts(LocalDateTime.now().minusDays(7))));
    return out;
  }

  // ───────────── Mapa de demanda ─────────────

  @GetMapping("/demand")
  public Map<String, Object> demand() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("unserved", jdbc.queryForList(
        "SELECT COALESCE(NULLIF(a.latitude,'')::double precision, c.latitude) AS lat, COALESCE(NULLIF(a.longitude,'')::double precision, c.longitude) AS lng, pr.name AS product " +
            "FROM order_ o LEFT JOIN address_ a ON a.id = o.address_id LEFT JOIN user_ c ON c.id = o.customer_id LEFT JOIN product_ pr ON pr.id = o.product_id " +
            "WHERE o.created_at >= ? AND (o.professional_id IS NULL OR o.status IN ('OPEN','BLOCKED')) LIMIT 2000", ts(LocalDateTime.now().minusDays(30))));
    out.put("orders", jdbc.queryForList(
        "SELECT COALESCE(NULLIF(a.latitude,'')::double precision, c.latitude) AS lat, COALESCE(NULLIF(a.longitude,'')::double precision, c.longitude) AS lng " +
            "FROM order_ o LEFT JOIN address_ a ON a.id = o.address_id LEFT JOIN user_ c ON c.id = o.customer_id WHERE o.created_at >= ? LIMIT 3000", ts(LocalDateTime.now().minusDays(30))));
    out.put("professionals", jdbc.queryForList(
        "SELECT u.id, u.name, u.latitude AS lat, u.longitude AS lng FROM user_ u JOIN user_role r ON r.user_id = u.id WHERE r.role = 'PROFESSIONAL' AND u.latitude IS NOT NULL LIMIT 2000"));
    out.put("live", jdbc.queryForList(
        "SELECT o.id, o.pro_lat AS lat, o.pro_lng AS lng, p.name AS \"professionalName\", o.status FROM order_ o LEFT JOIN user_ p ON p.id = o.professional_id " +
            "WHERE o.departed_at IS NOT NULL AND o.status IN ('CONFIRMED','AWAITING_PAYMENT','IN_PROGRESS') AND o.pro_lat IS NOT NULL"));
    return out;
  }

  // ───────────── Rede social ─────────────

  @GetMapping("/posts")
  public List<Map<String, Object>> posts() {
    return jdbc.queryForList(
        "SELECT p.id, p.caption, p.created_at AS \"createdAt\", u.name AS \"professionalName\", u.id AS \"professionalId\", " +
            "(SELECT ph.id FROM post_photo_ ph WHERE ph.post_id = p.id ORDER BY ph.position LIMIT 1) AS \"photoId\", " +
            "(SELECT COUNT(*) FROM post_like_ l WHERE l.post_id = p.id) AS likes, (SELECT COUNT(*) FROM post_comment_ c WHERE c.post_id = p.id) AS comments " +
            "FROM post_ p JOIN user_ u ON u.id = p.professional_id ORDER BY p.created_at DESC LIMIT 120");
  }

  @DeleteMapping("/posts/{id}")
  public ResponseEntity<?> deletePost(@PathVariable UUID id, @RequestParam(defaultValue = "") String reason) {
    User admin = userService.findAuthUser();
    List<Long> owner = jdbc.queryForList("SELECT professional_id FROM post_ WHERE id = ?", Long.class, id);
    jdbc.update("DELETE FROM post_photo_ WHERE post_id = ?", id);
    jdbc.update("DELETE FROM post_like_ WHERE post_id = ?", id);
    jdbc.update("DELETE FROM post_comment_ WHERE post_id = ?", id);
    jdbc.update("DELETE FROM post_ WHERE id = ?", id);
    settings.audit(admin, "POST_DELETE", id.toString(), reason);
    if (!owner.isEmpty()) {
      userRepository.findById(owner.get(0)).ifPresent(u -> notificationService.sendToUser(u, true,
          "Uma publicação sua foi removida pela equipe OPUS" + (reason.isBlank() ? "." : ": " + reason)));
    }
    return ResponseEntity.ok(Map.of("deleted", true));
  }

  // ───────────── Cupons ─────────────

  @GetMapping("/coupons")
  public List<Map<String, Object>> coupons() {
    return jdbc.queryForList("SELECT code, description, percent, amount, min_order AS \"minOrder\", valid_until AS \"validUntil\", max_uses AS \"maxUses\", used, active, created_at AS \"createdAt\" FROM coupon_ ORDER BY created_at DESC");
  }

  @PostMapping("/coupons")
  public ResponseEntity<?> createCoupon(@RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    String code = Objects.toString(body.get("code"), "").trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    if (code.length() < 3) return bad("O código precisa ter pelo menos 3 letras ou números.");
    BigDecimal percent = dec(body.get("percent"));
    BigDecimal amount = dec(body.get("amount"));
    if ((percent == null || percent.signum() <= 0) && (amount == null || amount.signum() <= 0)) return bad("Informe a porcentagem ou o valor do desconto.");
    if (percent != null && percent.compareTo(new BigDecimal("90")) > 0) return bad("Desconto máximo de 90%.");
    Integer maxUses = body.get("maxUses") == null || Objects.toString(body.get("maxUses")).isBlank() ? null : Integer.parseInt(Objects.toString(body.get("maxUses")));
    LocalDate until = body.get("validUntil") == null || Objects.toString(body.get("validUntil")).isBlank() ? null : LocalDate.parse(Objects.toString(body.get("validUntil")));
    try {
      jdbc.update("INSERT INTO coupon_ (code, description, percent, amount, min_order, valid_until, max_uses, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
          code, Objects.toString(body.get("description"), ""), percent, amount, Optional.ofNullable(dec(body.get("minOrder"))).orElse(BigDecimal.ZERO),
          until, maxUses, ts(LocalDateTime.now()));
    } catch (Exception e) {
      return bad("Já existe um cupom com esse código.");
    }
    settings.audit(admin, "COUPON_CREATE", code, null);
    return ResponseEntity.ok(coupons());
  }

  @PutMapping("/coupons/{code}/toggle")
  public ResponseEntity<?> toggleCoupon(@PathVariable String code) {
    User admin = userService.findAuthUser();
    jdbc.update("UPDATE coupon_ SET active = NOT active WHERE code = ?", code);
    settings.audit(admin, "COUPON_TOGGLE", code, null);
    return ResponseEntity.ok(coupons());
  }

  // ───────────── utilitários ─────────────

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  private BigDecimal money(String sql, Object... args) {
    BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class, args);
    return v == null ? BigDecimal.ZERO : v.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private static Timestamp ts(LocalDateTime t) {
    return Timestamp.valueOf(t);
  }

  private static LocalDateTime start(String range) {
    LocalDate today = LocalDate.now();
    switch (range) {
      case "day": return today.atStartOfDay();
      case "month": return today.minusDays(29).atStartOfDay();
      default: return today.minusDays(6).atStartOfDay();
    }
  }

  private static BigDecimal dec(Object o) {
    try {
      String s = Objects.toString(o, "").trim().replace(',', '.');
      return s.isEmpty() ? null : new BigDecimal(s);
    } catch (Exception e) {
      return null;
    }
  }
}
