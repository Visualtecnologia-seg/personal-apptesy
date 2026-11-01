package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.CompletionService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/** Ganhos do profissional (telas V3, V4 e V6). */
@RestController
@RequiredArgsConstructor
public class WalletController {

  private final UserService userService;
  private final WalletService walletService;
  private final JdbcTemplate jdbc;

  @GetMapping("/wallet")
  public ResponseEntity<?> wallet() {
    User me = userService.findAuthUser();
    Long pro = me.getId();
    LocalDateTime now = WalletService.now();
    LocalDateTime monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
    LocalDateTime prevStart = monthStart.minusMonths(1);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("available", walletService.available(pro));

    // No cofre: pagos pelo cliente e ainda não liberados
    List<Map<String, Object>> cofre = jdbc.queryForList(
        "SELECT o.id, o.status, o.service_value AS \"serviceValue\", o.requested_completion_at AS \"requestedCompletionAt\", " +
            "o.dispute_status AS \"disputeStatus\", o.date, o.start_time AS \"startTime\", o.client_checked_at AS \"clientCheckedAt\", " +
            "u.name AS \"clientName\", p.name AS \"service\" " +
            "FROM order_ o LEFT JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id " +
            "WHERE o.professional_id = ? AND o.pix_paid = true AND COALESCE(o.pix_released, false) = false " +
            "AND o.status NOT IN ('CANCELLED') ORDER BY o.date NULLS LAST, o.start_time NULLS LAST", pro);
    BigDecimal cofreTotal = BigDecimal.ZERO;
    for (Map<String, Object> c : cofre) {
      Object v = c.get("serviceValue");
      if (v instanceof BigDecimal) cofreTotal = cofreTotal.add((BigDecimal) v);
      Object req = c.get("requestedCompletionAt");
      if (req instanceof Timestamp) {
        c.put("autoReleaseAt", ((Timestamp) req).toLocalDateTime().plusHours(CompletionService.AUTO_RELEASE_HOURS));
      }
      c.put("stage", stage(c));
    }
    out.put("inCofre", cofreTotal);
    out.put("cofre", cofre);

    out.put("month", sum(pro, monthStart, now.plusDays(1)));
    out.put("prevMonth", sum(pro, prevStart, monthStart));

    out.put("entries", jdbc.queryForList(
        "SELECT id, kind, amount, status, description, pix_key AS \"pixKey\", order_id AS \"orderId\", created_at AS \"createdAt\" " +
            "FROM wallet_entry_ WHERE professional_id = ? ORDER BY created_at DESC LIMIT 40", pro));

    Map<String, Object> pix = new LinkedHashMap<>();
    pix.put("key", WalletService.mask(me.getPixKey()));
    pix.put("type", me.getPixKeyType());
    pix.put("name", me.getName());
    out.put("pix", pix);
    return ResponseEntity.ok(out);
  }

  @PostMapping("/wallet/withdraw")
  public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    BigDecimal amount;
    try {
      amount = new BigDecimal(Objects.toString(body.get("amount"), "0").replace(',', '.')).setScale(2, java.math.RoundingMode.DOWN);
    } catch (Exception e) {
      return bad("Valor inválido.");
    }
    try {
      String status = walletService.withdraw(me, amount);
      if (!"DONE".equals(status)) return bad("O banco não aceitou o saque agora. O valor continua na sua carteira. Tente de novo mais tarde.");
      return ResponseEntity.ok(Map.of("status", status, "available", walletService.available(me.getId())));
    } catch (IllegalArgumentException e) {
      return bad(e.getMessage());
    }
  }

  private BigDecimal sum(Long pro, LocalDateTime from, LocalDateTime to) {
    BigDecimal v = jdbc.queryForObject(
        "SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'CREDIT' AND created_at >= ? AND created_at < ?",
        BigDecimal.class, pro, Timestamp.valueOf(from), Timestamp.valueOf(to));
    return v == null ? BigDecimal.ZERO : v;
  }

  private static String stage(Map<String, Object> c) {
    String st = Objects.toString(c.get("status"), "");
    if ("OPEN".equalsIgnoreCase(Objects.toString(c.get("disputeStatus"), ""))) return "PROBLEM";
    if ("PENDING_CLIENT_CONFIRMATION".equals(st)) return c.get("clientCheckedAt") != null ? "CODE" : "CHECKING";
    if ("IN_PROGRESS".equals(st)) return "IN_PROGRESS";
    return "PAID";
  }
}
