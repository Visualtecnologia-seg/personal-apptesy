package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.MessageRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/**
 * "Saí para o atendimento" + acompanhamento ao vivo, taxa de cancelamento por km,
 * orçamento extra, PIX de teste e provas/decisão de disputas no painel.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TripController {

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final TransactionRepository transactionRepository;
  private final MessageRepository messageRepository;
  private final NotificationService notificationService;
  private final AsaasService asaasService;
  private final TripService tripService;
  private final ExtrasService extrasService;
  private final CompletionService completionService;
  private final TransactionService transactionService;
  private final JdbcTemplate jdbc;

  // ───────────── Deslocamento ─────────────

  @PostMapping("/orders/{id}/depart")
  public ResponseEntity<?> depart(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    if (o.getStatus() != Status.AWAITING_PAYMENT && o.getStatus() != Status.CONFIRMED) return bad("Este pedido não está aguardando o atendimento.");
    Double lat = dbl(body.get("lat")), lng = dbl(body.get("lng"));
    if (lat == null || lng == null) return bad("Não conseguimos a sua localização. Ligue o GPS e tente de novo.");
    boolean first = jdbc.queryForObject("SELECT departed_at IS NULL FROM order_ WHERE id = ?", Boolean.class, id);
    tripService.depart(id, lat, lng);
    if (first) {
      system(id, "🚗 " + firstName(me) + " saiu para o atendimento.");
      notificationService.sendToUser(o.getCustomer(), false, firstName(me) + " saiu e está a caminho. Acompanhe pelo app.");
    }
    return ResponseEntity.ok(tripService.trip(id));
  }

  @PostMapping("/orders/{id}/location")
  public ResponseEntity<?> location(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    Double lat = dbl(body.get("lat")), lng = dbl(body.get("lng"));
    if (lat == null || lng == null) return bad("Localização inválida.");
    tripService.location(id, lat, lng);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/orders/{id}/tracking")
  public ResponseEntity<?> tracking(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    Map<String, Object> t = tripService.trip(id);
    t.put("professionalName", o.getProfessional() != null ? o.getProfessional().getName() : null);
    t.put("professionalId", o.getProfessional() != null ? o.getProfessional().getId() : null);
    t.put("status", o.getStatus());
    return ResponseEntity.ok(t);
  }

  @GetMapping("/orders/{id}/cancel-preview")
  public ResponseEntity<?> cancelPreview(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    Map<String, Object> t = tripService.trip(id);
    BigDecimal fee = o.getProfessional() == null ? BigDecimal.ZERO : (BigDecimal) t.getOrDefault("cancelFee", BigDecimal.ZERO);
    BigDecimal paid = Boolean.TRUE.equals(o.getPixPaid()) && o.getTotalCost() != null
        ? o.getTotalCost().subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount()) : BigDecimal.ZERO;
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("departed", t.get("departed"));
    out.put("traveledKm", t.get("traveledKm"));
    out.put("fee", fee);
    out.put("paid", paid);
    out.put("refund", paid.subtract(fee).max(BigDecimal.ZERO));
    out.put("pricePerKm", TripService.PRICE_PER_KM);
    out.put("minFee", TripService.MIN_FEE);
    return ResponseEntity.ok(out);
  }

  // ───────────── PIX de teste ─────────────

  @PostMapping("/orders/{id}/pix/simulate")
  public ResponseEntity<?> simulatePix(@PathVariable UUID id) {
    if (!asaasService.isTestMode()) return bad("O pagamento de teste está desligado.");
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (Boolean.TRUE.equals(o.getPixPaid())) return ResponseEntity.ok(Map.of("status", o.getStatus()));
    Transaction tx = transactionRepository.findTransactionByOrderId(id).orElseGet(() -> asaasService.createPixCharge(o));
    tx.setPaymentStatus(PaymentStatus.PAID);
    transactionRepository.save(tx);
    o.setPixPaid(true);
    o.setStatus(Status.AWAITING_PAYMENT);
    orderRepository.save(o);
    try {
      if (o.getProfessional() != null) notificationService.sendPaymentConfirmedToProfessional(o);
      notificationService.sendPaymentLockedToCustomer(o);
    } catch (Exception e) {
      log.warn("Avisos do pagamento de teste não enviados: {}", e.getMessage());
    }
    return ResponseEntity.ok(Map.of("status", o.getStatus(), "testMode", true));
  }

  // ───────────── Orçamento extra ─────────────

  @GetMapping("/orders/{id}/extras")
  public ResponseEntity<?> extras(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    return ResponseEntity.ok(extrasService.list(id));
  }

  @PostMapping("/orders/{id}/extras/{extraId}/pix")
  public ResponseEntity<?> extraPix(@PathVariable UUID id, @PathVariable UUID extraId) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    try {
      Map<String, Object> c = new LinkedHashMap<>(extrasService.charge(o, extraId));
      c.put("testMode", asaasService.isTestMode());
      return ResponseEntity.ok(c);
    } catch (Exception e) {
      return bad(e.getMessage());
    }
  }

  @PostMapping("/orders/{id}/extras/{extraId}/simulate")
  public ResponseEntity<?> extraSimulate(@PathVariable UUID id, @PathVariable UUID extraId) {
    if (!asaasService.isTestMode()) return bad("O pagamento de teste está desligado.");
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    extrasService.markPaid(extraId);
    if (o.getProfessional() != null) notificationService.sendToUser(o.getProfessional(), true, "O cliente pagou o orçamento extra. Pode seguir com o serviço.");
    return ResponseEntity.ok(extrasService.list(id));
  }

  // ───────────── Painel: disputas e provas ─────────────

  @GetMapping("/admin/disputes")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> disputes(@RequestParam(defaultValue = "OPEN") String status) {
    String where = "ALL".equalsIgnoreCase(status) ? "o.dispute_status IS NOT NULL" : "o.dispute_status = 'OPEN'";
    return jdbc.queryForList(
        "SELECT o.id, o.status, o.date, o.total_cost AS \"totalCost\", o.service_value AS \"serviceValue\", o.dispute_status AS \"disputeStatus\", " +
            "o.dispute_reason AS \"disputeReason\", o.disputed_at AS \"disputedAt\", c.name AS \"customerName\", p.name AS \"professionalName\" " +
            "FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id LEFT JOIN user_ p ON p.id = o.professional_id WHERE " + where +
            " ORDER BY o.disputed_at DESC NULLS LAST LIMIT 200");
  }

  @GetMapping("/admin/orders/{id}/evidence")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> evidence(@PathVariable UUID id) {
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null) return bad("Pedido não encontrado.");
    Map<String, Object> out = new LinkedHashMap<>();
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", o.getId());
    order.put("status", o.getStatus());
    order.put("date", o.getDate());
    order.put("createdAt", o.getCreatedAt());
    order.put("address", o.getServiceAddress());
    order.put("product", o.getProduct() != null ? o.getProduct().getName() : null);
    order.put("totalCost", o.getTotalCost());
    order.put("serviceValue", o.getServiceValue());
    order.put("discount", o.getDiscountAmount());
    order.put("pixPaid", o.getPixPaid());
    order.put("pixReleased", o.getPixReleased());
    order.put("finishedAt", o.getRequestedCompletionAt());
    order.put("releasedAt", o.getPaymentReleasedAt());
    order.put("disputeStatus", o.getDisputeStatus());
    order.put("disputeReason", o.getDisputeReason());
    order.put("disputedAt", o.getDisputedAt());
    order.put("cancelledBy", o.getCancelledBy());
    order.put("cancelReason", o.getCancelReason());
    order.put("customer", person(o.getCustomer()));
    order.put("professional", person(o.getProfessional()));
    order.putAll(jdbc.queryForMap("SELECT cancel_fee AS \"cancelFee\", client_refund AS \"clientRefund\", dispute_resolution AS \"resolution\" FROM order_ WHERE id = ?", id));
    out.put("order", order);
    out.put("trip", tripService.trip(id));
    out.put("messages", messageRepository.findByOrderIdOrderByCreatedAtAsc(id).stream()
        .map(m -> {
          Map<String, Object> mm = new LinkedHashMap<>();
          mm.put("at", m.getCreatedAt());
          mm.put("from", m.getSenderName());
          mm.put("role", m.getSenderRole());
          mm.put("type", m.getMessageType());
          mm.put("text", m.getText());
          mm.put("imageUrl", m.getImageUrl());
          mm.put("quoteTotal", m.getQuoteTotalCost() != null ? m.getQuoteTotalCost() : m.getQuoteAmount());
          mm.put("quoteStatus", m.getQuoteStatus());
          return mm;
        }).toArray());
    out.put("calls", CallController.history(jdbc, id));
    out.put("photos", jdbc.queryForList("SELECT id, kind, created_at AS \"createdAt\" FROM order_photo_ WHERE order_id = ? ORDER BY created_at", id));
    out.put("extras", extrasService.list(id));
    List<Long> people = new ArrayList<>();
    if (o.getCustomer() != null) people.add(o.getCustomer().getId());
    if (o.getProfessional() != null) people.add(o.getProfessional().getId());
    List<Map<String, Object>> consents = new ArrayList<>();
    for (Long uid : people) {
      consents.addAll(jdbc.queryForList(
          "SELECT c.user_id AS \"userId\", u.name, c.version, c.accepted_at AS \"acceptedAt\" FROM protection_consent_ c JOIN user_ u ON u.id = c.user_id WHERE c.user_id = ?", uid));
    }
    out.put("consents", consents);
    out.put("blockedAttempts", jdbc.queryForList(
        "SELECT * FROM blocked_attempt_ WHERE order_id = ? ORDER BY 1 DESC LIMIT 50", id));
    return ResponseEntity.ok(out);
  }

  @PostMapping("/admin/orders/{id}/dispute/resolve")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> resolve(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null) return bad("Pedido não encontrado.");
    if (!"OPEN".equalsIgnoreCase(o.getDisputeStatus())) return bad("Esta disputa não está aberta.");
    String decision = Objects.toString(body.get("decision"), "");
    String note = Objects.toString(body.get("note"), "").trim();
    if (note.length() < 5) return bad("Escreva o motivo da decisão.");
    String resolution = firstName(admin) + " · " + note;
    if ("RELEASE".equals(decision)) {
      o.setDisputeStatus("RESOLVED_PRO");
      orderRepository.save(o);
      jdbc.update("UPDATE order_ SET dispute_resolution = ? WHERE id = ?", resolution, id);
      completionService.finalizeAndRelease(o, "disputa decidida a favor do profissional");
      notificationService.sendToUser(o.getCustomer(), false, "A equipe OPUS analisou o seu pedido e liberou o pagamento ao profissional. Motivo: " + note);
      notificationService.sendToUser(o.getProfessional(), true, "Disputa resolvida a seu favor. O pagamento foi liberado.");
    } else if ("REFUND".equals(decision)) {
      o.setDisputeStatus("RESOLVED_CLIENT");
      o.setStatus(Status.CANCELLED);
      orderRepository.save(o);
      BigDecimal paid = Boolean.TRUE.equals(o.getPixPaid()) && o.getTotalCost() != null
          ? o.getTotalCost().subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount()).add(extrasService.paidTotal(id))
          : BigDecimal.ZERO;
      jdbc.update("UPDATE order_ SET dispute_resolution = ?, client_refund = ? WHERE id = ?", resolution, paid, id);
      try { transactionService.refundPayment(o); } catch (Exception e) { log.warn("Reembolso não registrado: {}", e.getMessage()); }
      notificationService.sendToUser(o.getCustomer(), false, "A equipe OPUS decidiu a seu favor. O valor pago será devolvido.");
      if (o.getProfessional() != null) notificationService.sendToUser(o.getProfessional(), true, "A disputa foi decidida a favor do cliente. Motivo: " + note);
    } else {
      return bad("Decisão inválida.");
    }
    return ResponseEntity.ok(Map.of("disputeStatus", o.getDisputeStatus()));
  }

  // ───────────── utilitários ─────────────

  private Map<String, Object> person(User u) {
    if (u == null) return null;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("name", u.getName());
    m.put("username", u.getUsername());
    m.put("email", u.getEmail());
    return m;
  }

  private void system(UUID orderId, String text) {
    try {
      messageRepository.save(Message.builder().orderId(orderId).senderName("OPUS").senderRole("SYSTEM").messageType("SYSTEM").text(text).build());
    } catch (Exception e) {
      log.warn("Mensagem do sistema não salva: {}", e.getMessage());
    }
  }

  private static boolean isPro(Order o, User me) {
    return o.getProfessional() != null && o.getProfessional().getId().equals(me.getId());
  }

  private static boolean isCustomer(Order o, User me) {
    return o.getCustomer() != null && o.getCustomer().getId().equals(me.getId());
  }

  private static Double dbl(Object o) {
    try {
      return o == null ? null : Double.parseDouble(o.toString());
    } catch (Exception e) {
      return null;
    }
  }
}
