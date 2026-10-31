package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.MessageRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/**
 * Chamadas de áudio e vídeo 1-para-1 (M4) dentro do pedido.
 * O áudio/vídeo vai direto entre os celulares (WebRTC); o servidor só faz a "apresentação"
 * (sinalização) e registra cada chamada no chat e no histórico — prova para disputas.
 */
@Slf4j
@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
public class CallController {

  private static final int RING_SECONDS = 45;

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final MessageRepository messageRepository;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  @Value("${TURN_URL:}")
  private String turnUrl;
  @Value("${TURN_USERNAME:}")
  private String turnUser;
  @Value("${TURN_CREDENTIAL:}")
  private String turnPass;

  @GetMapping("/ice")
  public Map<String, Object> ice() {
    List<Map<String, Object>> servers = new ArrayList<>();
    servers.add(Map.of("urls", List.of("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")));
    if (turnUrl != null && !turnUrl.isBlank()) {
      Map<String, Object> t = new HashMap<>();
      t.put("urls", Arrays.asList(turnUrl.split(",")));
      t.put("username", turnUser);
      t.put("credential", turnPass);
      servers.add(t);
    }
    return Map.of("iceServers", servers);
  }

  @PostMapping
  public ResponseEntity<?> start(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    UUID orderId;
    try {
      orderId = UUID.fromString(Objects.toString(body.get("orderId"), ""));
    } catch (Exception e) {
      return bad("Pedido inválido.");
    }
    Order o = orderRepository.findById(orderId).orElse(null);
    if (o == null || o.getProfessional() == null || o.getCustomer() == null) return bad("A chamada fica disponível quando um profissional aceitar o pedido.");
    boolean iAmPro = o.getProfessional().getId().equals(me.getId());
    boolean iAmClient = o.getCustomer().getId().equals(me.getId());
    if (!iAmPro && !iAmClient) return bad("Pedido não encontrado.");
    if (o.getStatus() == Status.CANCELLED || o.getStatus() == Status.BLOCKED) return bad("Este pedido foi encerrado.");
    String kind = "audio".equalsIgnoreCase(Objects.toString(body.get("kind"), "")) ? "audio" : "video";
    User other = iAmPro ? o.getCustomer() : o.getProfessional();

    // Encerra chamadas antigas que ficaram "tocando"
    jdbc.update("UPDATE call_ SET status = 'MISSED', ended_at = ? WHERE order_id = ? AND status = 'RINGING'",
        Timestamp.valueOf(LocalDateTime.now()), orderId);

    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO call_ (id, order_id, caller_id, callee_id, kind, status, created_at) VALUES (?, ?, ?, ?, ?, 'RINGING', ?)",
        id, orderId, me.getId(), other.getId(), kind, Timestamp.valueOf(LocalDateTime.now()));
    try {
      notificationService.sendToUser(other, !iAmPro,
          "📞 " + firstName(me) + " está te ligando (" + ("audio".equals(kind) ? "áudio" : "vídeo") + "). Abra o OPUS para atender.");
    } catch (Exception e) {
      log.warn("Aviso de chamada não enviado: {}", e.getMessage());
    }
    return ResponseEntity.ok(view(id, me));
  }

  /** O app consulta de tempos em tempos se há alguém ligando. */
  @GetMapping("/incoming")
  public ResponseEntity<?> incoming() {
    User me = userService.findAuthUser();
    List<UUID> ids = jdbc.queryForList(
        "SELECT id FROM call_ WHERE callee_id = ? AND status = 'RINGING' AND created_at > ? ORDER BY created_at DESC LIMIT 1",
        UUID.class, me.getId(), Timestamp.valueOf(LocalDateTime.now().minusSeconds(RING_SECONDS)));
    if (ids.isEmpty()) return ResponseEntity.noContent().build();
    return ResponseEntity.ok(view(ids.get(0), me));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> v = view(id, me);
    return v == null ? bad("Chamada não encontrada.") : ResponseEntity.ok(v);
  }

  @PostMapping("/{id}/answer")
  public ResponseEntity<?> answer(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    int n = jdbc.update("UPDATE call_ SET status = 'ACTIVE', answered_at = ? WHERE id = ? AND callee_id = ? AND status = 'RINGING'",
        Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    return n > 0 ? ResponseEntity.ok(view(id, me)) : bad("A chamada já terminou.");
  }

  @PostMapping("/{id}/decline")
  public ResponseEntity<?> decline(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    int n = jdbc.update("UPDATE call_ SET status = 'DECLINED', ended_at = ?, ended_by = ? WHERE id = ? AND callee_id = ? AND status = 'RINGING'",
        Timestamp.valueOf(LocalDateTime.now()), me.getId(), id, me.getId());
    if (n > 0) logToChat(id);
    return ResponseEntity.ok(Map.of("status", "DECLINED"));
  }

  @PostMapping("/{id}/end")
  public ResponseEntity<?> end(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    int n = jdbc.update("UPDATE call_ SET status = CASE WHEN status = 'ACTIVE' THEN 'ENDED' ELSE 'MISSED' END, ended_at = ?, ended_by = ? " +
        "WHERE id = ? AND (caller_id = ? OR callee_id = ?) AND status IN ('RINGING', 'ACTIVE')", now, me.getId(), id, me.getId(), me.getId());
    if (n > 0) logToChat(id);
    return ResponseEntity.ok(view(id, me));
  }

  // ───────────── Sinalização (WebRTC) ─────────────

  @PostMapping("/{id}/signal")
  public ResponseEntity<?> signal(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    if (!participant(id, me)) return bad("Chamada não encontrada.");
    String kind = Objects.toString(body.get("kind"), "");
    if (!List.of("offer", "answer", "ice", "bye").contains(kind)) return bad("Sinal inválido.");
    String payload = Objects.toString(body.get("payload"), "");
    if (payload.length() > 100_000) return bad("Sinal grande demais.");
    jdbc.update("INSERT INTO call_signal_ (call_id, from_user, kind, payload, created_at) VALUES (?, ?, ?, ?, ?)",
        id, me.getId(), kind, payload, Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/{id}/signal")
  public ResponseEntity<?> signals(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
    User me = userService.findAuthUser();
    if (!participant(id, me)) return bad("Chamada não encontrada.");
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, kind, payload FROM call_signal_ WHERE call_id = ? AND from_user <> ? AND id > ? ORDER BY id",
        id, me.getId(), after);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("signals", rows);
    out.put("status", jdbc.queryForObject("SELECT status FROM call_ WHERE id = ?", String.class, id));
    return ResponseEntity.ok(out);
  }

  // ───────────── Histórico do pedido ─────────────

  @GetMapping("/order/{orderId}")
  public ResponseEntity<?> history(@PathVariable UUID orderId) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(orderId).orElse(null);
    if (o == null) return bad("Pedido não encontrado.");
    boolean allowed = SocialController.hasRole(me, br.com.personalfighters.model.Role.ADMIN)
        || (o.getCustomer() != null && o.getCustomer().getId().equals(me.getId()))
        || (o.getProfessional() != null && o.getProfessional().getId().equals(me.getId()));
    if (!allowed) return bad("Pedido não encontrado.");
    return ResponseEntity.ok(history(jdbc, orderId));
  }

  static List<Map<String, Object>> history(JdbcTemplate jdbc, UUID orderId) {
    return jdbc.queryForList(
        "SELECT c.id, c.kind, c.status, c.created_at AS \"createdAt\", c.answered_at AS \"answeredAt\", c.ended_at AS \"endedAt\", " +
            "c.caller_id AS \"callerId\", u.name AS \"callerName\", " +
            "CASE WHEN c.answered_at IS NOT NULL AND c.ended_at IS NOT NULL THEN EXTRACT(EPOCH FROM (c.ended_at - c.answered_at))::INT ELSE 0 END AS \"seconds\" " +
            "FROM call_ c LEFT JOIN user_ u ON u.id = c.caller_id WHERE c.order_id = ? ORDER BY c.created_at DESC", orderId);
  }

  // ───────────── utilitários ─────────────

  private boolean participant(UUID id, User me) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM call_ WHERE id = ? AND (caller_id = ? OR callee_id = ?)", Long.class, id, me.getId(), me.getId());
    return n != null && n > 0;
  }

  private Map<String, Object> view(UUID id, User me) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM call_ WHERE id = ?", id);
    if (rows.isEmpty()) return null;
    Map<String, Object> c = rows.get(0);
    Long caller = ((Number) c.get("caller_id")).longValue();
    Long callee = ((Number) c.get("callee_id")).longValue();
    if (!me.getId().equals(caller) && !me.getId().equals(callee)) return null;
    String status = (String) c.get("status");
    LocalDateTime created = ((Timestamp) c.get("created_at")).toLocalDateTime();
    if ("RINGING".equals(status) && created.isBefore(LocalDateTime.now().minusSeconds(RING_SECONDS))) {
      int n = jdbc.update("UPDATE call_ SET status = 'MISSED', ended_at = ? WHERE id = ? AND status = 'RINGING'",
          Timestamp.valueOf(LocalDateTime.now()), id);
      if (n > 0) logToChat(id);
      status = "MISSED";
    }
    Long otherId = me.getId().equals(caller) ? callee : caller;
    User other = null;
    try { other = userService.findById(otherId); } catch (Exception ignored) { }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("orderId", c.get("order_id"));
    out.put("kind", c.get("kind"));
    out.put("status", status);
    out.put("iAmCaller", me.getId().equals(caller));
    out.put("otherId", otherId);
    out.put("otherName", other != null ? other.getName() : "");
    out.put("createdAt", c.get("created_at"));
    out.put("answeredAt", c.get("answered_at"));
    return out;
  }

  /** Registra o resultado da chamada como mensagem do sistema no chat do pedido. */
  private void logToChat(UUID id) {
    try {
      Map<String, Object> c = jdbc.queryForMap("SELECT * FROM call_ WHERE id = ?", id);
      String kind = "audio".equals(c.get("kind")) ? "Chamada de áudio" : "Chamada de vídeo";
      String status = (String) c.get("status");
      String text;
      if ("ENDED".equals(status) && c.get("answered_at") != null) {
        long s = Duration.between(((Timestamp) c.get("answered_at")).toLocalDateTime(),
            ((Timestamp) c.get("ended_at")).toLocalDateTime()).getSeconds();
        text = "📞 " + kind + " · " + (s / 60) + " min " + (s % 60) + " s";
      } else if ("DECLINED".equals(status)) {
        text = "📞 " + kind + " recusada";
      } else {
        text = "📞 " + kind + " não atendida";
      }
      Message m = Message.builder()
          .orderId((UUID) c.get("order_id"))
          .senderId(((Number) c.get("caller_id")).longValue())
          .senderName("OPUS")
          .senderRole("SYSTEM")
          .messageType("SYSTEM")
          .text(text)
          .build();
      messageRepository.save(m);
    } catch (Exception e) {
      log.warn("Registro da chamada {} no chat falhou: {}", id, e.getMessage());
    }
  }
}
