package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.CompletionService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Conclusão do serviço (P1/P2), fotos do serviço e linha do tempo do pagamento.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CompletionController {

  private static final int MAX_PHOTOS = 6;
  private static final int MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  private final OrderRepository orderRepository;
  private final UserService userService;
  private final CompletionService completionService;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  // ───────────── Profissional: "Terminei" ─────────────

  @PostMapping("/orders/{id}/finish")
  public ResponseEntity<?> finish(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    if (o.getStatus() != Status.IN_PROGRESS && o.getStatus() != Status.CONFIRMED && o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) {
      return bad("Este pedido não está em andamento.");
    }
    int saved = 0;
    Object photos = body == null ? null : body.get("photos");
    if (photos instanceof List) {
      for (Object item : (List<?>) photos) {
        if (saved >= MAX_PHOTOS) break;
        if (savePhoto(o.getId(), "AFTER", item)) saved++;
      }
    }
    o.setStatus(Status.PENDING_CLIENT_CONFIRMATION);
    o.setRequestedCompletionAt(completionService.now());
    String code = completionService.newCode(o);
    orderRepository.save(o);
    notificationService.sendToUser(o.getCustomer(), false,
        firstName(o.getProfessional()) + " marcou o serviço como pronto. Agora é com você: olhe, teste e grave um vídeo. Se estiver tudo certo, mostre o seu código a ele para liberar o pagamento.");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", code);
    out.put("photosSaved", saved);
    out.put("autoReleaseAt", o.getRequestedCompletionAt().plusHours(CompletionService.AUTO_RELEASE_HOURS));
    return ResponseEntity.ok(out);
  }

  /** O profissional consulta o código atual (gera outro se venceu). */
  @GetMapping("/orders/{id}/finish-code")
  public ResponseEntity<?> finishCode(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("Este pedido não está aguardando aprovação.");
    if (o.getFinishCode() == null || o.getFinishCodeExpiresAt() == null || o.getFinishCodeExpiresAt().isBefore(completionService.now())) {
      completionService.newCode(o);
      orderRepository.save(o);
    }
    return ResponseEntity.ok(Map.of("code", o.getFinishCode()));
  }

  // ───────────── Cliente: confere e aprova ─────────────

  @PostMapping("/orders/{id}/approve")
  public ResponseEntity<?> approve(@PathVariable UUID id, @RequestBody Map<String, String> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("Este pedido não está aguardando a sua aprovação.");
    if (completionService.disputeOpen(o)) return bad("Há uma disputa aberta neste pedido. Nossa equipe vai resolver.");
    String code = Objects.toString(body.get("code"), "").replaceAll("[^0-9]", "");
    if (o.getFinishCode() == null || !o.getFinishCode().equals(code)) {
      return bad("Código incorreto. Confira o código na tela do profissional.");
    }
    if (o.getFinishCodeExpiresAt() != null && o.getFinishCodeExpiresAt().isBefore(completionService.now())) {
      return bad("O código venceu. Peça ao profissional para atualizar a tela.");
    }
    completionService.finalizeAndRelease(o, "aprovado pelo cliente");
    return ResponseEntity.ok(Map.of("status", "DONE", "released", Boolean.TRUE.equals(o.getPixReleased())));
  }

  @PostMapping("/orders/{id}/problem")
  public ResponseEntity<?> problem(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (Boolean.TRUE.equals(o.getPixReleased())) return bad("O pagamento já foi liberado. Use a garantia de 30 dias.");
    String reason = Objects.toString(body.get("reason"), "").trim();
    if (reason.length() < 10) return bad("Conte com mais detalhes o que aconteceu.");
    Object photos = body.get("photos");
    if (photos instanceof List) {
      int n = 0;
      for (Object item : (List<?>) photos) {
        if (n >= MAX_PHOTOS) break;
        if (savePhoto(o.getId(), "PROBLEM", item)) n++;
      }
    }
    o.setDisputeStatus("OPEN");
    o.setDisputeReason(reason);
    o.setDisputedAt(completionService.now());
    orderRepository.save(o);
    notificationService.sendToUser(o.getProfessional(), true,
        firstName(o.getCustomer()) + " relatou um problema no serviço. O pagamento fica guardado até a equipe OPUS analisar.");
    return ResponseEntity.ok(Map.of("disputeStatus", "OPEN"));
  }

  // ───────────── Fotos do serviço ─────────────

  @GetMapping("/orders/photos/{photoId}")
  public ResponseEntity<byte[]> photo(@PathVariable UUID photoId) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, data FROM order_photo_ WHERE id = ?", photoId);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) rows.get(0).get("content_type")))
        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
        .body((byte[]) rows.get(0).get("data"));
  }

  // ───────────── Linha do tempo do pagamento ─────────────

  @GetMapping("/orders/{id}/timeline")
  public ResponseEntity<?> timeline(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isCustomer(o, me) || isPro(o, me))) return bad("Pedido não encontrado.");

    BigDecimal total = o.getTotalCost();
    BigDecimal service = o.getServiceValue() != null ? o.getServiceValue() : Pricing.serviceFromTotal(total);
    BigDecimal discount = o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount();
    BigDecimal fee = (total != null && service != null) ? total.subtract(service) : null;
    BigDecimal toPay = total == null ? null : total.subtract(discount).max(BigDecimal.ZERO);

    boolean paid = Boolean.TRUE.equals(o.getPixPaid());
    boolean started = o.getStatus() == Status.IN_PROGRESS || o.getStatus() == Status.PENDING_CLIENT_CONFIRMATION
        || o.getStatus() == Status.DONE || o.getStatus() == Status.REVIEWED;
    boolean finished = o.getRequestedCompletionAt() != null || o.getStatus() == Status.DONE || o.getStatus() == Status.REVIEWED;
    boolean approved = o.getStatus() == Status.DONE || o.getStatus() == Status.REVIEWED;
    boolean released = Boolean.TRUE.equals(o.getPixReleased());
    boolean dispute = completionService.disputeOpen(o);

    Map<String, Object> extra = jdbc.queryForMap("SELECT client_checked_at, departed_at, arrived_at FROM order_ WHERE id = ?", o.getId());
    LocalDateTime checkedAt = extra.get("client_checked_at") == null ? null : ((Timestamp) extra.get("client_checked_at")).toLocalDateTime();
    LocalDateTime arrivedAt = extra.get("arrived_at") == null ? null : ((Timestamp) extra.get("arrived_at")).toLocalDateTime();
    boolean arrived = arrivedAt != null || Boolean.TRUE.equals(o.getClientConfirmedArrival()) || Boolean.TRUE.equals(o.getProConfirmedArrival());

    List<Map<String, Object>> steps = new ArrayList<>();
    steps.add(step("PAID", "Pagamento guardado no cofre OPUS", paid, null));
    steps.add(step("ARRIVED", "Profissional chegou", arrived || finished, arrivedAt));
    steps.add(step("STARTED", "Serviço em andamento", started, null));
    steps.add(step("FINISHED", "Profissional terminou", finished, o.getRequestedCompletionAt()));
    steps.add(step("CHECKED", "Cliente conferiu o serviço", checkedAt != null || approved, checkedAt));
    steps.add(step("APPROVED", dispute ? "Problema relatado: em análise" : "Código entregue e pagamento aprovado", approved && !dispute, o.getDoneAt()));
    steps.add(step("RELEASED", "Pagamento enviado ao profissional", released, o.getPaymentReleasedAt()));

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", o.getStatus());
    out.put("clientTotal", total);
    out.put("serviceValue", service);
    out.put("fee", fee);
    out.put("discount", discount);
    out.put("toPay", toPay);
    out.put("paid", paid);
    out.put("released", released);
    out.put("disputeOpen", dispute);
    out.put("requestedCompletionAt", o.getRequestedCompletionAt());
    out.put("autoReleaseAt", o.getRequestedCompletionAt() == null ? null : o.getRequestedCompletionAt().plusHours(CompletionService.AUTO_RELEASE_HOURS));
    out.put("steps", steps);
    List<Map<String, Object>> photos = new ArrayList<>();
    for (Map<String, Object> r : jdbc.queryForList("SELECT id, kind FROM order_photo_ WHERE order_id = ? ORDER BY created_at", o.getId())) {
      photos.add(Map.of("id", r.get("id"), "kind", r.get("kind"), "url", "/orders/photos/" + r.get("id")));
    }
    out.put("photos", photos);
    return ResponseEntity.ok(out);
  }

  // ───────────── utilitários ─────────────

  private boolean savePhoto(UUID orderId, String kind, Object item) {
    try {
      String raw = Objects.toString(item, "");
      String type = "image/jpeg";
      int comma = raw.indexOf(',');
      if (raw.startsWith("data:") && comma > 0) {
        int semi = raw.indexOf(';');
        type = raw.substring(5, semi > 0 ? semi : comma);
        raw = raw.substring(comma + 1);
      }
      if (!type.startsWith("image/")) return false;
      byte[] data = Base64.getMimeDecoder().decode(raw);
      if (data.length == 0 || data.length > MAX_PHOTO_BYTES) return false;
      jdbc.update("INSERT INTO order_photo_ (id, order_id, kind, content_type, data, created_at) VALUES (?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(), orderId, kind, type, data, Timestamp.valueOf(LocalDateTime.now()));
      return true;
    } catch (Exception e) {
      log.warn("Foto do pedido {} não salva: {}", orderId, e.getMessage());
      return false;
    }
  }

  private static Map<String, Object> step(String key, String label, boolean done, LocalDateTime at) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", key);
    m.put("label", label);
    m.put("done", done);
    m.put("at", at);
    return m;
  }

  private static boolean isPro(Order o, User me) {
    return o.getProfessional() != null && o.getProfessional().getId().equals(me.getId());
  }

  private static boolean isCustomer(Order o, User me) {
    return o.getCustomer() != null && o.getCustomer().getId().equals(me.getId());
  }

  private static String firstName(User u) {
    if (u == null || u.getName() == null) return "O profissional";
    return u.getName().trim().split("\\s+")[0];
  }

  private static ResponseEntity<?> bad(String msg) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }
}
