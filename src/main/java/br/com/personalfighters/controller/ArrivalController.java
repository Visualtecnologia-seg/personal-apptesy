package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.QRTokenService;
import br.com.personalfighters.service.impl.AsaasServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class ArrivalController {

  private final OrderRepository orderRepository;
  private final NotificationService notificationService;
  private final AsaasService asaasService;
  private final QRTokenService qrTokenService;
  private final br.com.personalfighters.service.TripService tripService;
  private final br.com.personalfighters.service.UserService userService;
  private final br.com.personalfighters.service.CompletionService completionService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  /** Cliente que não confirma a chegada: depois disso o profissional que já chegou pode começar sozinho. */
  private static final int SOLO_START_MINUTES = 15;

  // Quem pode fazer cada passo: antes qualquer conta logada podia (até confirmar a chegada pelos dois lados).
  private boolean admin() {
    var a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
  }
  private boolean isPro(Order o) { var u = userService.findAuthUser(); return o.getProfessional() != null && o.getProfessional().getId().equals(u.getId()); }
  private boolean isClient(Order o) { var u = userService.findAuthUser(); return o.getCustomer() != null && o.getCustomer().getId().equals(u.getId()); }
  private static ResponseEntity<Map<String, Object>> forbidden() {
    Map<String, Object> r = new HashMap<>(); r.put("error", "Acesso não permitido para este pedido.");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(r);
  }

  // ── GPS Proximity ─────────────────────────────────────────────────────────

  @PostMapping("/{orderId}/check-proximity")
  public ResponseEntity<Map<String, Object>> checkProximity(
      @PathVariable UUID orderId,
      @RequestParam double professionalLat,
      @RequestParam double professionalLng) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isPro(order)) return forbidden();

    User client = order.getCustomer();
    if (client == null || client.getLatitude() == null || client.getLongitude() == null) {
      result.put("isNear", false);
      result.put("distanceMeters", -1);
      result.put("message", "Localização do cliente não disponível");
      return ResponseEntity.ok(result);
    }

    double distance = AsaasServiceImpl.calculateDistanceMeters(
        professionalLat, professionalLng, client.getLatitude(), client.getLongitude());
    boolean isNear = distance <= 300.0;
    result.put("isNear", isNear);
    result.put("distanceMeters", Math.round(distance));

    if (isNear && Status.AWAITING_PAYMENT.equals(order.getStatus())) {
      notificationService.sendArrivalNotifications(order);
      result.put("notificationsSent", true);
    } else {
      result.put("notificationsSent", false);
    }
    return ResponseEntity.ok(result);
  }

  // ── Arrival QR ────────────────────────────────────────────────────────────

  /** Professional calls this to get a 6-char arrival code to show the client. */
  @PostMapping("/{orderId}/arrival-qr")
  public ResponseEntity<Map<String, Object>> generateArrivalQR(@PathVariable UUID orderId) {
    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isPro(order)) return forbidden();
    if (!Status.AWAITING_PAYMENT.equals(order.getStatus())) {
      result.put("error", "Pedido não está no estado correto para gerar QR de chegada.");
      return ResponseEntity.badRequest().body(result);
    }
    String code = qrTokenService.generateArrivalCode(orderId);
    String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" + code
        + "&bgcolor=0B1426&color=FFFFFF&margin=10&qzone=2";
    result.put("code", code);
    result.put("qrImageUrl", qrUrl);
    result.put("expiresInMinutes", 10);
    return ResponseEntity.ok(result);
  }

  /** Client submits the code they read from the professional's screen → confirms arrival → IN_PROGRESS. */
  @PostMapping("/{orderId}/scan-arrival")
  public ResponseEntity<Map<String, Object>> scanArrival(
      @PathVariable UUID orderId,
      @RequestParam String code) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isClient(order)) return forbidden();
    if (order.getStatus() != Status.AWAITING_PAYMENT) {
      result.put("error", "A chegada só é confirmada depois que o pagamento está no cofre.");
      return ResponseEntity.badRequest().body(result);
    }

    boolean valid = qrTokenService.validateAndUse(code, orderId, "ARRIVAL");
    if (!valid) {
      result.put("error", "Código inválido, expirado ou já utilizado. Peça um novo código ao profissional.");
      return ResponseEntity.badRequest().body(result);
    }

    order.setProConfirmedArrival(true);
    order.setClientConfirmedArrival(true);
    order.setStatus(Status.IN_PROGRESS);
    orderRepository.save(order);
    tripService.markArrived(order.getId());

    result.put("status", "IN_PROGRESS");
    result.put("message", "Chegada confirmada! Serviço iniciado.");
    return ResponseEntity.ok(result);
  }

  // ── Legacy button-based arrival confirm ───────────────────────────────────

  @PostMapping("/{orderId}/confirm-arrival")
  public ResponseEntity<Map<String, Object>> confirmArrival(
      @PathVariable UUID orderId,
      @RequestParam String role) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isPro(order) && !isClient(order)) return forbidden();

    if (order.getStatus() != Status.AWAITING_PAYMENT) {
      result.put("error", "A chegada só é confirmada depois que o pagamento está no cofre.");
      return ResponseEntity.badRequest().body(result);
    }
    role = isPro(order) ? "PROFESSIONAL" : isClient(order) ? "CLIENT" : role; // o papel vem de quem está logado, não do pedido
    // Cada lado marca só a sua confirmação direto no banco (antes, os dois confirmando juntos apagavam um ao outro e o pedido travava)
    boolean pro = "PROFESSIONAL".equalsIgnoreCase(role);
    if (pro) jdbc.update("UPDATE order_ SET pro_confirmed_arrival = TRUE, arrived_at = COALESCE(arrived_at, NOW()) WHERE id = ? AND status = 'AWAITING_PAYMENT'", orderId);
    else if ("CLIENT".equalsIgnoreCase(role)) jdbc.update("UPDATE order_ SET client_confirmed_arrival = TRUE WHERE id = ? AND status = 'AWAITING_PAYMENT'", orderId);
    int started = jdbc.update("UPDATE order_ SET status = 'IN_PROGRESS' WHERE id = ? AND status = 'AWAITING_PAYMENT' AND COALESCE(pro_confirmed_arrival, FALSE) = TRUE AND COALESCE(client_confirmed_arrival, FALSE) = TRUE", orderId);
    // Cliente que some sem confirmar: 15 minutos depois de o profissional chegar, ele pode começar sozinho
    boolean solo = false;
    if (started == 0 && pro) {
      started = jdbc.update("UPDATE order_ SET status = 'IN_PROGRESS' WHERE id = ? AND status = 'AWAITING_PAYMENT' AND COALESCE(pro_confirmed_arrival, FALSE) = TRUE AND arrived_at < NOW() - make_interval(mins => ?)", orderId, SOLO_START_MINUTES);
      solo = started == 1;
    }
    java.util.Map<String, Object> now = jdbc.queryForMap("SELECT status, COALESCE(pro_confirmed_arrival, FALSE) AS p, COALESCE(client_confirmed_arrival, FALSE) AS c FROM order_ WHERE id = ?", orderId);
    Boolean proConfirmed = (Boolean) now.get("p");
    Boolean clientConfirmed = (Boolean) now.get("c");
    if ("IN_PROGRESS".equals(now.get("status"))) {
      result.put("status", "IN_PROGRESS");
      result.put("message", solo ? "O cliente não confirmou em " + SOLO_START_MINUTES + " minutos: serviço iniciado. Ele foi avisado." : "Serviço iniciado! Boa sorte!");
      if (solo && order.getCustomer() != null) notificationService.sendToUser(order.getCustomer(), true, "O profissional chegou e começou o serviço. Se ele não está aí, use \"Relatar problema\" no pedido.");
    } else {
      result.put("status", String.valueOf(now.get("status")));
      result.put("message", pro ? "Aguardando o cliente confirmar. Se ele não confirmar em " + SOLO_START_MINUTES + " minutos, você pode começar." : "Aguardando confirmação da outra parte.");
      if (pro && order.getCustomer() != null && !Boolean.TRUE.equals(order.getProConfirmedArrival()))
        notificationService.sendToUser(order.getCustomer(), true, "O profissional chegou! Confirme a chegada no app para o serviço começar.");
    }
    result.put("proConfirmed", proConfirmed);
    result.put("clientConfirmed", clientConfirmed);
    return ResponseEntity.ok(result);
  }

  // ── Completion Flow ───────────────────────────────────────────────────────

  /** Professional presses "Finalizar" → status becomes PENDING_CLIENT_CONFIRMATION. */
  @PostMapping("/{orderId}/request-completion")
  public ResponseEntity<Map<String, Object>> requestCompletion(@PathVariable UUID orderId) {
    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isPro(order)) return forbidden();
    if (!Status.IN_PROGRESS.equals(order.getStatus())) {
      result.put("error", "Pedido não está em andamento.");
      return ResponseEntity.badRequest().body(result);
    }
    order.setStatus(Status.PENDING_CLIENT_CONFIRMATION);
    order.setRequestedCompletionAt(LocalDateTime.now());
    orderRepository.save(order);
    result.put("status", "PENDING_CLIENT_CONFIRMATION");
    result.put("message", "Solicitação enviada ao cliente. Aguardando confirmação.");
    return ResponseEntity.ok(result);
  }

  /** Client generates a completion QR code to show the professional. */
  @PostMapping("/{orderId}/completion-qr")
  public ResponseEntity<Map<String, Object>> generateCompletionQR(@PathVariable UUID orderId) {
    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isClient(order)) return forbidden();
    if (!Status.PENDING_CLIENT_CONFIRMATION.equals(order.getStatus())) {
      result.put("error", "Pedido não aguarda confirmação de conclusão.");
      return ResponseEntity.badRequest().body(result);
    }
    String code = qrTokenService.generateCompletionCode(orderId);
    String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" + code
        + "&bgcolor=0B1426&color=22C55E&margin=10&qzone=2";
    result.put("code", code);
    result.put("qrImageUrl", qrUrl);
    result.put("expiresInMinutes", 10);
    return ResponseEntity.ok(result);
  }

  /** Professional scans client's completion QR → DONE. */
  @PostMapping("/{orderId}/scan-completion")
  public ResponseEntity<Map<String, Object>> scanCompletion(
      @PathVariable UUID orderId,
      @RequestParam String code) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isPro(order)) return forbidden();

    // Só vale com o pedido aguardando a conferência e sem reclamação (antes um código velho concluía depois de o cliente pedir correção)
    if (!Status.PENDING_CLIENT_CONFIRMATION.equals(order.getStatus()) || completionService.disputeOpen(order)) {
      result.put("error", "Este pedido não está aguardando a conferência do cliente.");
      return ResponseEntity.badRequest().body(result);
    }
    boolean valid = qrTokenService.validateAndUse(code, orderId, "COMPLETION");
    if (!valid) {
      result.put("error", "Código inválido, expirado ou já utilizado. Peça um novo código ao cliente.");
      return ResponseEntity.badRequest().body(result);
    }

    completionService.finalizeAndRelease(order, "código de conclusão lido pelo profissional");
    result.put("status", "DONE");
    result.put("message", "Serviço concluído! Aguardando avaliação.");
    return ResponseEntity.ok(result);
  }

  /** Client rejects completion: CORRECTION returns to IN_PROGRESS, DISPUTE opens dispute. */
  @PostMapping("/{orderId}/reject-completion")
  public ResponseEntity<Map<String, Object>> rejectCompletion(
      @PathVariable UUID orderId,
      @RequestParam String action,
      @RequestParam(required = false, defaultValue = "") String reason) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin() && !isClient(order)) return forbidden();
    if (!Status.PENDING_CLIENT_CONFIRMATION.equals(order.getStatus())) {
      result.put("error", "Pedido não aguarda confirmação de conclusão.");
      return ResponseEntity.badRequest().body(result);
    }

    // Pedido de correção ou reclamação: o código de conclusão que o cliente já tinha gerado deixa de valer
    jdbc.update("UPDATE qr_token_ SET used = TRUE WHERE order_id = ? AND type = 'COMPLETION'", orderId);
    if ("CORRECTION".equalsIgnoreCase(action)) {
      // No máximo 2 correções: depois disso é reclamação com a equipe OPUS (antes dava para adiar o pagamento para sempre)
      if (jdbc.update("UPDATE order_ SET correction_count = COALESCE(correction_count, 0) + 1 WHERE id = ? AND COALESCE(correction_count, 0) < 2", orderId) == 0) {
        result.put("error", "Você já pediu 2 correções. Se ainda há problema, use \"Relatar problema\" e a equipe OPUS analisa.");
        return ResponseEntity.badRequest().body(result);
      }
      order.setStatus(Status.IN_PROGRESS);
      order.setRequestedCompletionAt(null);
      orderRepository.save(order);
      tripService.markArrived(order.getId());
      result.put("status", "IN_PROGRESS");
      result.put("message", "Pedido voltou para Em andamento. O profissional será notificado.");
    } else if ("DISPUTE".equalsIgnoreCase(action)) {
      order.setDisputeStatus("OPEN");
      order.setDisputeReason(reason.isEmpty() ? "Cliente rejeitou conclusão do serviço." : reason);
      order.setDisputedAt(LocalDateTime.now());
      orderRepository.save(order);
      result.put("status", "PENDING_CLIENT_CONFIRMATION");
      result.put("disputeOpened", true);
      result.put("message", "Disputa aberta. Nossa equipe analisará em até 48h. Pagamento bloqueado.");
    } else {
      result.put("error", "Ação inválida. Use CORRECTION ou DISPUTE.");
      return ResponseEntity.badRequest().body(result);
    }
    return ResponseEntity.ok(result);
  }

  // ── Payment Release ───────────────────────────────────────────────────────

  @PostMapping("/{orderId}/release-payment")
  public ResponseEntity<Map<String, Object>> releasePayment(
      @PathVariable UUID orderId,
      @RequestParam(required = false) Long userId) {

    Map<String, Object> result = new HashMap<>();
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null) { result.put("error", "Pedido não encontrado"); return ResponseEntity.badRequest().body(result); }
    if (!admin()) return forbidden();

    if (userId != null) {
      boolean isCustomer = order.getCustomer() != null && order.getCustomer().getId().equals(userId);
      boolean isProfessional = order.getProfessional() != null && order.getProfessional().getId().equals(userId);
      if (!isCustomer && !isProfessional) {
        result.put("error", "Acesso não autorizado para liberar este pagamento.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
      }
    }

    if (order.getDisputeStatus() != null && "OPEN".equals(order.getDisputeStatus())) {
      result.put("error", "Pagamento bloqueado — disputa em aberto. Aguarde a resolução.");
      return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    if (!Boolean.TRUE.equals(order.getPixReleased())) {
      asaasService.releasePaymentToProfessional(order);
      order.setPixReleased(true);
      order.setStatus(Status.REVIEWED);
      orderRepository.save(order);
      notificationService.sendPaymentReleasedToProfessional(order);
      result.put("released", true);
    } else {
      result.put("released", false);
      result.put("message", "Pagamento já foi liberado anteriormente.");
    }
    return ResponseEntity.ok(result);
  }
}
