package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Extras do chat e da proteção:
 *  - mensagens não lidas por pedido (contador);
 *  - aceite do guia de proteção;
 *  - reserva de pontos (painel).
 */
@RestController
@RequiredArgsConstructor
public class ChatExtrasController {

  /** Versão do texto do guia de proteção. Suba quando o texto mudar (ex.: gravação ligada). */
  public static final int PROTECTION_VERSION = 1;

  private final JdbcTemplate jdbc;
  private final UserService userService;

  @PutMapping("/messages/read")
  public ResponseEntity<?> markRead(@RequestParam UUID orderId) {
    User me = userService.findAuthUser();
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    int n = jdbc.update("UPDATE chat_read_ SET last_read_at = ? WHERE order_id = ? AND user_id = ?", now, orderId, me.getId());
    if (n == 0) jdbc.update("INSERT INTO chat_read_ (order_id, user_id, last_read_at) VALUES (?, ?, ?)", orderId, me.getId(), now);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  /** {orderId: quantidade} das mensagens dos outros que eu ainda não li, nos meus pedidos. */
  @GetMapping("/messages/unread")
  public Map<String, Integer> unread() {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT m.order_id AS oid, COUNT(*) AS n FROM message_ m " +
        "JOIN order_ o ON o.id = m.order_id " +
        "LEFT JOIN chat_read_ r ON r.order_id = m.order_id AND r.user_id = ? " +
        "WHERE (o.customer_id = ? OR o.professional_id = ?) " +
        "AND (m.sender_id IS NULL OR m.sender_id <> ?) " +
        "AND (r.last_read_at IS NULL OR m.created_at > r.last_read_at) " +
        "GROUP BY m.order_id",
        me.getId(), me.getId(), me.getId(), me.getId());
    Map<String, Integer> out = new HashMap<>();
    for (Map<String, Object> r : rows) out.put(String.valueOf(r.get("oid")), ((Number) r.get("n")).intValue());
    return out;
  }

  @GetMapping("/protection/consent")
  public Map<String, Object> consent() {
    User me = userService.findAuthUser();
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM protection_consent_ WHERE user_id = ? AND version = ?",
        Integer.class, me.getId(), PROTECTION_VERSION);
    return Map.of("accepted", n != null && n > 0, "version", PROTECTION_VERSION, "recording", false);
  }

  @PostMapping("/protection/consent")
  public Map<String, Object> accept() {
    User me = userService.findAuthUser();
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM protection_consent_ WHERE user_id = ? AND version = ?",
        Integer.class, me.getId(), PROTECTION_VERSION);
    if (n == null || n == 0) {
      jdbc.update("INSERT INTO protection_consent_ (user_id, version, accepted_at) VALUES (?, ?, ?)",
          me.getId(), PROTECTION_VERSION, Timestamp.valueOf(LocalDateTime.now()));
    }
    return Map.of("accepted", true, "version", PROTECTION_VERSION);
  }

  /** Reserva de pontos: R$ 1 a cada 10 pontos ainda não usados pelos clientes. */
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/admin/points-reserve")
  public Map<String, Object> pointsReserve() {
    Long outstanding = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM points_", Long.class);
    Long given = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM points_ WHERE amount > 0", Long.class);
    Long used = jdbc.queryForObject("SELECT COALESCE(-SUM(amount), 0) FROM points_ WHERE amount < 0", Long.class);
    long out = outstanding == null ? 0 : outstanding;
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("pointsOutstanding", out);
    m.put("reserveValue", out / 10.0);
    m.put("pointsGiven", given);
    m.put("pointsUsed", used);
    return m;
  }
}
