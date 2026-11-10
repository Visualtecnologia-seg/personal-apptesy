package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.MessageRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/** Orçamento para vários profissionais, pagamento por etapas, áudio no chat, filtro de recusas e mapa de demanda. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MoreController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final UserService userService;
  private final OrderService orderService;
  private final OrderRepository orderRepository;
  private final MessageRepository messageRepository;
  private final NotificationService notificationService;
  private final WalletService walletService;
  private final JdbcTemplate jdbc;

  private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(Z)); }

  // ───────────── Pedir orçamento para 2 ou 3 profissionais ─────────────

  /** O app cria um pedido para cada profissional (POST /orders) e depois junta todos aqui num grupo de orçamento. */
  @PostMapping("/quotes")
  public ResponseEntity<?> createQuotes(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Object ids = b.get("orderIds");
    if (!(ids instanceof List) || ((List<?>) ids).isEmpty()) return bad("Nenhum pedido para juntar.");
    String note = Objects.toString(b.get("note"), "").trim();
    UUID group = UUID.randomUUID();
    List<UUID> linked = new ArrayList<>();
    for (Object id : (List<?>) ids) {
      try {
        UUID oid = UUID.fromString(id.toString());
        Order o = orderRepository.findById(oid).orElse(null);
        if (o == null || !isCustomer(o, me)) continue;
        jdbc.update("UPDATE order_ SET quote_group = ?, request_note = ? WHERE id = ?", group, note.isEmpty() ? null : note, oid);
        linked.add(oid);
        if (!note.isEmpty()) {
          messageRepository.save(Message.builder().orderId(oid).senderId(me.getId()).senderName(me.getName()).senderRole("CUSTOMER")
              .messageType("TEXT").text("Pedido de orçamento: " + note).build());
        }
        try { notificationService.sendToUser(o.getProfessional(), true, firstName(me) + " pediu orçamento para você e mais " + (((List<?>) ids).size() - 1) + " profissional(is). Responda rápido no chat!"); } catch (Exception ignored) { }
      } catch (Exception e) { log.warn("Pedido {} não entrou no grupo: {}", id, e.getMessage()); }
    }
    if (linked.isEmpty()) return bad("Não foi possível pedir os orçamentos.");
    return ResponseEntity.ok(Map.of("group", group, "orders", linked));
  }

  @GetMapping("/quotes")
  public List<Map<String, Object>> myGroups() {
    User me = userService.findAuthUser();
    return jdbc.queryForList("SELECT quote_group AS \"group\", MIN(created_at) AS \"createdAt\", COUNT(*) AS n, " +
        "SUM(CASE WHEN status NOT IN ('OPEN','CANCELLED','BLOCKED') THEN 1 ELSE 0 END) AS \"answered\" " +
        "FROM order_ WHERE customer_id = ? AND quote_group IS NOT NULL GROUP BY quote_group ORDER BY MIN(created_at) DESC LIMIT 20", me.getId());
  }

  @GetMapping("/quotes/{group}")
  public List<Map<String, Object>> group(@PathVariable UUID group) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT o.id, o.status, o.total_cost AS \"total\", o.service_value AS \"labor\", o.warranty_days AS \"warranty\", o.date, o.start_time AS \"startTime\", " +
            "u.id AS \"proId\", u.name AS \"proName\", (SELECT ROUND(AVG(r.rating)::numeric,1) FROM review_ r WHERE r.professional_id = u.id) AS \"rating\", " +
            "(SELECT COUNT(*) FROM order_ d WHERE d.professional_id = u.id AND d.status IN ('DONE','REVIEWED')) AS \"done\", " +
            "(SELECT COALESCE(SUM(m.qty * m.price),0) FROM order_material_ m WHERE m.order_id = o.id AND m.buyer <> 'HAVE') AS \"materials\", " +
            "(SELECT m2.quote_total_cost FROM message_ m2 WHERE m2.order_id = o.id AND m2.message_type = 'QUOTE' ORDER BY m2.created_at DESC LIMIT 1) AS \"quoteTotal\" " +
            "FROM order_ o JOIN user_ u ON u.id = o.professional_id WHERE o.quote_group = ? AND o.customer_id = ? ORDER BY o.created_at", group, me.getId());
    return rows;
  }

  /** Cliente escolhe um orçamento: os outros pedidos do grupo são cancelados sem custo. */
  @PostMapping("/quotes/{group}/choose")
  public ResponseEntity<?> choose(@PathVariable UUID group, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    UUID chosen;
    try { chosen = UUID.fromString(Objects.toString(b.get("orderId"))); } catch (Exception e) { return bad("Escolha um orçamento."); }
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM order_ WHERE quote_group = ? AND customer_id = ?", group, me.getId());
    for (Map<String, Object> r : rows) {
      UUID id = (UUID) r.get("id");
      if (id.equals(chosen)) continue;
      Order o = orderRepository.findById(id).orElse(null);
      if (o == null || Boolean.TRUE.equals(o.getPixPaid()) || o.getStatus() == Status.CANCELLED) continue;
      o.setStatus(Status.CANCELLED);
      o.setCancelledBy("CLIENT");
      o.setCancelReason("O cliente escolheu outro orçamento");
      orderRepository.save(o);
      try { notificationService.sendToUser(o.getProfessional(), true, firstName(me) + " escolheu outro orçamento desta vez. Obrigado por responder!"); } catch (Exception ignored) { }
    }
    jdbc.update("UPDATE order_ SET quote_group = NULL WHERE id = ?", chosen);
    return ResponseEntity.ok(Map.of("orderId", chosen));
  }

  // ───────────── Pagamento por etapas (liberação do cofre em partes) ─────────────

  @GetMapping("/orders/{id}/stages")
  public ResponseEntity<?> stages(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    return ResponseEntity.ok(stageState(o));
  }

  /** Profissional divide o serviço em etapas (ex.: 30/40/30). O cliente paga tudo no cofre e cada etapa é liberada quando ele aprova. */
  @PutMapping("/orders/{id}/stages")
  public ResponseEntity<?> setStages(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    Long started = jdbc.queryForObject("SELECT COUNT(*) FROM order_stage_ WHERE order_id = ? AND status <> 'PENDING'", Long.class, id);
    if (started != null && started > 0) return bad("As etapas já começaram e não podem mudar.");
    Object raw = b.get("percents");
    if (!(raw instanceof List) || ((List<?>) raw).size() < 2 || ((List<?>) raw).size() > 5) return bad("Divida em 2 a 5 etapas.");
    List<Integer> ps = new ArrayList<>();
    for (Object p : (List<?>) raw) ps.add(AreaService.bd(p, "0").intValue());
    if (ps.stream().mapToInt(Integer::intValue).sum() != 100 || ps.stream().anyMatch(p -> p <= 0)) return bad("As etapas precisam somar 100%.");
    List<?> labels = b.get("labels") instanceof List ? (List<?>) b.get("labels") : List.of();
    jdbc.update("DELETE FROM order_stage_ WHERE order_id = ?", id);
    for (int i = 0; i < ps.size(); i++) {
      String label = i < labels.size() ? Objects.toString(labels.get(i), "") : "";
      jdbc.update("INSERT INTO order_stage_ (order_id, idx, percent, label, status) VALUES (?,?,?,?, 'PENDING')", id, i + 1, ps.get(i), label.isBlank() ? "Etapa " + (i + 1) : label);
    }
    notificationService.sendToUser(o.getCustomer(), false, firstName(o.getProfessional()) + " dividiu o serviço em " + ps.size() + " etapas. Você aprova cada etapa e só ela é liberada.");
    return ResponseEntity.ok(stageState(o));
  }

  @PostMapping("/orders/{id}/stages/{idx}/done")
  public ResponseEntity<?> stageDone(@PathVariable UUID id, @PathVariable int idx) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    int last = n("SELECT COALESCE(MAX(idx),0) FROM order_stage_ WHERE order_id = ?", id);
    if (idx >= last) return bad("Na última etapa use \"Terminei\": o cliente confere e entrega o código.");
    jdbc.update("UPDATE order_stage_ SET status = 'DONE', done_at = ? WHERE order_id = ? AND idx = ? AND status = 'PENDING'", now(), id, idx);
    notificationService.sendToUser(o.getCustomer(), false, firstName(o.getProfessional()) + " terminou a etapa " + idx + ". Confira e aprove para liberar essa parte.");
    return ResponseEntity.ok(stageState(o));
  }

  @PostMapping("/orders/{id}/stages/{idx}/approve")
  public ResponseEntity<?> stageApprove(@PathVariable UUID id, @PathVariable int idx) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (!Boolean.TRUE.equals(o.getPixPaid())) return bad("O pagamento ainda não está no cofre.");
    if (n("SELECT COUNT(*) FROM protection_session_ WHERE order_id = ? AND active = TRUE AND level >= 3", id) > 0) return bad("Há um SOS ativo neste pedido. Os pagamentos ficam pausados até a equipe encerrar.");
    int ok = jdbc.update("UPDATE order_stage_ SET status = 'APPROVED', approved_at = ? WHERE order_id = ? AND idx = ? AND status = 'DONE'", now(), id, idx);
    if (ok == 0) return bad("Esta etapa ainda não foi marcada como feita.");
    Integer pct = jdbc.queryForObject("SELECT percent FROM order_stage_ WHERE order_id = ? AND idx = ?", Integer.class, id, idx);
    BigDecimal part = Optional.ofNullable(o.getServiceValue()).orElse(BigDecimal.ZERO).multiply(BigDecimal.valueOf(pct == null ? 0 : pct)).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    walletService.creditStage(o, idx, part);
    notificationService.sendToUser(o.getProfessional(), true, firstName(me) + " aprovou a etapa " + idx + ". R$ " + part.toPlainString().replace('.', ',') + " caíram na sua carteira.");
    return ResponseEntity.ok(stageState(o));
  }

  private Map<String, Object> stageState(Order o) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> st = jdbc.queryForList("SELECT idx, percent, label, status, done_at AS \"doneAt\", approved_at AS \"approvedAt\" FROM order_stage_ WHERE order_id = ? ORDER BY idx", o.getId());
    BigDecimal sv = Optional.ofNullable(o.getServiceValue()).orElse(BigDecimal.ZERO);
    for (Map<String, Object> s : st) s.put("value", sv.multiply(BigDecimal.valueOf(((Number) s.get("percent")).intValue())).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN));
    out.put("stages", st);
    out.put("serviceValue", sv);
    out.put("paid", Boolean.TRUE.equals(o.getPixPaid()));
    return out;
  }

  // ───────────── Áudio no chat ─────────────

  @PostMapping(value = "/chat/{orderId}/audio", consumes = "multipart/form-data")
  public ResponseEntity<?> audio(@PathVariable UUID orderId, @RequestParam("file") MultipartFile file, @RequestParam(value = "seconds", required = false) Integer seconds) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(orderId).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    if (file == null || file.isEmpty() || file.getSize() > 8L * 1024 * 1024) return bad("Áudio vazio ou grande demais (até 2 minutos).");
    String quota = br.com.personalfighters.util.StorageGuard.audioQuota(jdbc, orderId, me.getId());
    if (quota != null) return bad(quota);
    try {
      UUID id = UUID.randomUUID();
      jdbc.update("INSERT INTO chat_audio_ (id, order_id, sender_id, content_type, data, seconds, created_at) VALUES (?,?,?,?,?,?,?)",
          id, orderId, me.getId(), br.com.personalfighters.util.MediaTypes.audioOr(file.getContentType(), "audio/m4a"), file.getBytes(), seconds, now());
      boolean pro = isPro(o, me);
      Message m = messageRepository.save(Message.builder().orderId(orderId).senderId(me.getId()).senderName(me.getName())
          .senderRole(pro ? "PROFESSIONAL" : "CUSTOMER").messageType("AUDIO").text("🎤 Áudio" + (seconds != null ? " (" + seconds + "s)" : ""))
          .imageUrl("/chat/audio/" + id).build());
      try { notificationService.sendToUser(pro ? o.getCustomer() : o.getProfessional(), !pro, firstName(me) + " mandou um áudio no chat."); } catch (Exception ignored) { }
      return ResponseEntity.ok(m);
    } catch (Exception e) {
      return bad("Não foi possível enviar o áudio.");
    }
  }

  @GetMapping("/chat/audio/{id}")
  public ResponseEntity<byte[]> audioGet(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> r = jdbc.queryForList("SELECT a.content_type, a.data, o.customer_id, o.professional_id FROM chat_audio_ a JOIN order_ o ON o.id = a.order_id WHERE a.id = ?", id);
    if (r.isEmpty()) return ResponseEntity.notFound().build();
    Map<String, Object> x = r.get(0);
    if (!me.getId().equals(((Number) x.get("customer_id")).longValue()) && !me.getId().equals(((Number) x.get("professional_id")).longValue())) return ResponseEntity.status(403).build();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(Objects.toString(x.get("content_type"), "audio/m4a"))).body((byte[]) x.get("data"));
  }

  // ───────────── O app aprende com as recusas ─────────────

  /** Regras tiradas dos motivos de recusa: serviços que o profissional não faz, distância máxima e valor mínimo. */
  @GetMapping("/pro/refusal-rules")
  public Map<String, Object> rules() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("skipProductIds", jdbc.queryForList(
        "SELECT DISTINCT o.product_id FROM order_refusal_ r JOIN order_ o ON o.id = r.order_id WHERE r.professional_id = ? AND r.reason = 'Não faço esse serviço' AND o.product_id IS NOT NULL", Long.class, me.getId()));
    BigDecimal minValue = jdbc.queryForObject(
        "SELECT MAX(COALESCE(o.service_value, o.total_cost)) FROM order_refusal_ r JOIN order_ o ON o.id = r.order_id WHERE r.professional_id = ? AND r.reason = 'Valor baixo' AND r.created_at > ?",
        BigDecimal.class, me.getId(), Timestamp.valueOf(LocalDateTime.now(Z).minusDays(60)));
    out.put("minValue", minValue);
    Double maxKm = null;
    if (me.getLatitude() != null) {
      List<Map<String, Object>> far = jdbc.queryForList(
          "SELECT a.latitude, a.longitude FROM order_refusal_ r JOIN order_ o ON o.id = r.order_id JOIN address_ a ON a.id = o.address_id " +
              "WHERE r.professional_id = ? AND r.reason IN ('Longe demais','Fora da minha área') AND r.created_at > ?", me.getId(), Timestamp.valueOf(LocalDateTime.now(Z).minusDays(60)));
      if (far.size() >= 2) {
        double min = Double.MAX_VALUE;
        for (Map<String, Object> f : far) {
          try {
            double km = AreaService.km(me.getLatitude(), me.getLongitude(), Double.parseDouble(f.get("latitude").toString()), Double.parseDouble(f.get("longitude").toString()));
            min = Math.min(min, km);
          } catch (Exception ignored) { }
        }
        if (min < Double.MAX_VALUE) maxKm = Math.max(2, Math.floor(min * 0.9));
      }
    }
    out.put("maxKm", maxKm);
    out.put("home", me.getLatitude() == null ? null : Map.of("lat", me.getLatitude(), "lng", me.getLongitude()));
    return out;
  }

  // ───────────── Online / offline do profissional ─────────────

  /** O app manda o id, mas o servidor sempre usa o profissional logado (ninguém muda o status de outro). */
  @PutMapping("/professionals/{id}/online-status")
  public ResponseEntity<?> onlineStatus(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    boolean on = Boolean.parseBoolean(Objects.toString(b.get("isOnline"), "false"));
    int n = jdbc.update("UPDATE professional_ SET is_online = ?, online_at = CASE WHEN ? THEN NOW() ELSE online_at END WHERE id = (SELECT professional_id FROM user_ WHERE id = ?)", on, on, me.getId());
    if (n == 0) return bad("Só profissionais podem ficar online.");
    return ResponseEntity.ok(Map.of("online", on));
  }

  // ───────────── Mapa de calor da demanda ─────────────

  @GetMapping("/pro/demand-map")
  public Map<String, Object> demandMap() {
    User me = userService.findAuthUser();
    List<Map<String, Object>> pts = jdbc.queryForList(
        "SELECT ROUND(CAST(a.latitude AS NUMERIC), 2) AS lat, ROUND(CAST(a.longitude AS NUMERIC), 2) AS lng, COUNT(*) AS n " +
            "FROM order_ o JOIN address_ a ON a.id = o.address_id WHERE o.created_at >= ? AND a.latitude ~ '^-?[0-9.]+$' AND a.longitude ~ '^-?[0-9.]+$' " +
            "AND (o.product_id IN (SELECT DISTINCT product_id FROM order_ WHERE professional_id = ?) OR NOT EXISTS (SELECT 1 FROM order_ WHERE professional_id = ?)) " +
            "GROUP BY 1, 2 ORDER BY n DESC LIMIT 200", Timestamp.valueOf(LocalDateTime.now(Z).minusDays(90)), me.getId(), me.getId());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("points", pts);
    out.put("center", me.getLatitude() == null ? null : Map.of("lat", me.getLatitude(), "lng", me.getLongitude()));
    return out;
  }

  private int n(String sql, Object... a) { Integer v = jdbc.queryForObject(sql, Integer.class, a); return v == null ? 0 : v; }
  private static boolean isPro(Order o, User me) { return o.getProfessional() != null && o.getProfessional().getId().equals(me.getId()); }
  private static boolean isCustomer(Order o, User me) { return o.getCustomer() != null && o.getCustomer().getId().equals(me.getId()); }
}
