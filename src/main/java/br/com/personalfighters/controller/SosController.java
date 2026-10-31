package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.SettingsService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/**
 * Botão SOS: avisa a equipe OPUS (painel + celular dos administradores), manda a localização ao vivo
 * e guarda o áudio em partes. O contato de confiança é avisado pelo SMS/WhatsApp do próprio celular.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SosController {

  private static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final NotificationService notificationService;
  private final SettingsService settings;

  // ───────────── Contato de confiança ─────────────

  @GetMapping("/sos/contact")
  public ResponseEntity<?> contact() {
    User me = userService.findAuthUser();
    List<Map<String, Object>> r = jdbc.queryForList("SELECT name, phone FROM emergency_contact_ WHERE user_id = ?", me.getId());
    return r.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(r.get(0));
  }

  @PutMapping("/sos/contact")
  public ResponseEntity<?> saveContact(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    String name = Objects.toString(body.get("name"), "").trim();
    String phone = Objects.toString(body.get("phone"), "").replaceAll("[^0-9+]", "");
    if (name.isEmpty() || phone.length() < 10) return bad("Informe o nome e o celular com DDD.");
    jdbc.update("INSERT INTO emergency_contact_ (user_id, name, phone, updated_at) VALUES (?, ?, ?, ?) " +
        "ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, phone = EXCLUDED.phone, updated_at = EXCLUDED.updated_at",
        me.getId(), name, phone, Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(Map.of("name", name, "phone", phone));
  }

  // ───────────── Acionar e acompanhar ─────────────

  @PostMapping("/sos")
  public ResponseEntity<?> start(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    UUID orderId = null;
    try { orderId = UUID.fromString(Objects.toString(body.get("orderId"), "")); } catch (Exception ignored) { }
    if (orderId != null) {
      Order o = orderRepository.findById(orderId).orElse(null);
      boolean mine = o != null && ((o.getCustomer() != null && o.getCustomer().getId().equals(me.getId()))
          || (o.getProfessional() != null && o.getProfessional().getId().equals(me.getId())));
      if (!mine) orderId = null;
    }
    Double lat = dbl(body.get("lat")), lng = dbl(body.get("lng"));
    List<UUID> open = jdbc.queryForList("SELECT id FROM protection_session_ WHERE user_id = ? AND active = TRUE AND level >= 3 ORDER BY started_at DESC LIMIT 1", UUID.class, me.getId());
    UUID id;
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    if (!open.isEmpty()) {
      id = open.get(0);
      jdbc.update("UPDATE protection_session_ SET lat = COALESCE(?, lat), lng = COALESCE(?, lng), loc_at = ? WHERE id = ?", lat, lng, now, id);
    } else {
      id = UUID.randomUUID();
      jdbc.update("INSERT INTO protection_session_ (id, order_id, user_id, level, active, started_at, escalated_at, lat, lng, loc_at) VALUES (?, ?, ?, 3, TRUE, ?, ?, ?, ?, ?)",
          id, orderId == null ? new UUID(0, 0) : orderId, me.getId(), now, now, lat, lng, now);
      alertAdmins("🆘 SOS: " + me.getName() + " pediu ajuda pelo OPUS. Abra o painel agora.");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    List<Map<String, Object>> c = jdbc.queryForList("SELECT name, phone FROM emergency_contact_ WHERE user_id = ?", me.getId());
    out.put("contact", c.isEmpty() ? null : c.get(0));
    return ResponseEntity.ok(out);
  }

  @PostMapping("/sos/{id}/location")
  public ResponseEntity<?> location(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Double lat = dbl(body.get("lat")), lng = dbl(body.get("lng"));
    if (lat == null || lng == null) return bad("Localização inválida.");
    jdbc.update("UPDATE protection_session_ SET lat = ?, lng = ?, loc_at = ? WHERE id = ? AND user_id = ? AND active = TRUE",
        lat, lng, Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/sos/{id}/audio")
  public ResponseEntity<?> audio(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM protection_session_ WHERE id = ? AND user_id = ?", Long.class, id, me.getId());
    if (n == null || n == 0) return bad("SOS não encontrado.");
    try {
      String raw = Objects.toString(body.get("audio"), "");
      String type = Objects.toString(body.get("contentType"), "audio/mp4");
      int comma = raw.indexOf(',');
      if (raw.startsWith("data:") && comma > 0) raw = raw.substring(comma + 1);
      byte[] data = Base64.getMimeDecoder().decode(raw);
      if (data.length == 0 || data.length > MAX_AUDIO_BYTES) return bad("Áudio inválido.");
      if (!type.startsWith("audio/")) type = "audio/mp4";
      jdbc.update("INSERT INTO protection_audio_ (id, session_id, content_type, data, created_at) VALUES (?, ?, ?, ?, ?)",
          UUID.randomUUID(), id, type, data, Timestamp.valueOf(LocalDateTime.now()));
      return ResponseEntity.ok(Map.of("ok", true));
    } catch (Exception e) {
      return bad("Áudio inválido.");
    }
  }

  @PostMapping("/sos/{id}/stop")
  public ResponseEntity<?> stop(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("UPDATE protection_session_ SET active = FALSE, stopped_at = ? WHERE id = ? AND user_id = ?",
        Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    alertAdmins("SOS encerrado por " + firstName(me) + ": a pessoa avisou que está bem.");
    return ResponseEntity.ok(Map.of("active", false));
  }

  // ───────────── Painel ─────────────

  @GetMapping("/admin/sos")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> alerts(@RequestParam(defaultValue = "false") boolean all) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT s.id, s.order_id AS \"orderId\", s.user_id AS \"userId\", s.level, s.active, s.started_at AS \"startedAt\", s.stopped_at AS \"stoppedAt\", " +
            "s.lat, s.lng, s.loc_at AS \"locAt\", s.acknowledged_at AS \"ackAt\", s.acknowledged_by AS \"ackBy\", s.note, " +
            "u.name, u.phone_number AS phone, u.gender, ec.name AS \"contactName\", ec.phone AS \"contactPhone\", " +
            "o.service_address AS \"serviceAddress\", o.status AS \"orderStatus\", " +
            "CASE WHEN o.customer_id = s.user_id THEN p.name ELSE c.name END AS \"otherName\", " +
            "CASE WHEN o.customer_id = s.user_id THEN p.phone_number ELSE c.phone_number END AS \"otherPhone\" " +
            "FROM protection_session_ s JOIN user_ u ON u.id = s.user_id LEFT JOIN emergency_contact_ ec ON ec.user_id = s.user_id " +
            "LEFT JOIN order_ o ON o.id = s.order_id LEFT JOIN user_ p ON p.id = o.professional_id LEFT JOIN user_ c ON c.id = o.customer_id " +
            "WHERE s.level >= 3 " + (all ? "" : "AND s.active = TRUE ") + "ORDER BY s.started_at DESC LIMIT 100");
    for (Map<String, Object> r : rows) {
      r.put("audios", jdbc.queryForList("SELECT id, created_at AS \"createdAt\" FROM protection_audio_ WHERE session_id = ? ORDER BY created_at", r.get("id")));
    }
    return rows;
  }

  @PostMapping("/admin/sos/{id}/ack")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> ack(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    User admin = userService.findAuthUser();
    String note = body == null ? "" : Objects.toString(body.get("note"), "").trim();
    boolean close = body != null && Boolean.TRUE.equals(body.get("close"));
    jdbc.update("UPDATE protection_session_ SET acknowledged_at = COALESCE(acknowledged_at, ?), acknowledged_by = COALESCE(acknowledged_by, ?), note = ?, " +
            "active = CASE WHEN ? THEN FALSE ELSE active END, stopped_at = CASE WHEN ? THEN ? ELSE stopped_at END WHERE id = ?",
        Timestamp.valueOf(LocalDateTime.now()), admin.getName(), note.isEmpty() ? null : note, close, close, Timestamp.valueOf(LocalDateTime.now()), id);
    settings.audit(admin, close ? "SOS_CLOSE" : "SOS_ACK", id.toString(), note);
    try {
      Long uid = jdbc.queryForObject("SELECT user_id FROM protection_session_ WHERE id = ?", Long.class, id);
      userRepository.findById(uid).ifPresent(u -> {
        boolean pro = SocialController.hasRole(u, Role.PROFESSIONAL);
        notificationService.sendToUser(u, pro, close ? "A equipe OPUS encerrou o seu SOS." : "A equipe OPUS recebeu o seu SOS e está acompanhando.");
      });
    } catch (Exception ignored) { }
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/admin/sos/audio/{id}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<byte[]> audioFile(@PathVariable UUID id) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT content_type, data FROM protection_audio_ WHERE id = ?", id);
    if (r.isEmpty()) return ResponseEntity.notFound().build();
    return ResponseEntity.ok().contentType(MediaType.parseMediaType((String) r.get(0).get("content_type"))).body((byte[]) r.get(0).get("data"));
  }

  private void alertAdmins(String text) {
    try {
      for (Long id : jdbc.queryForList("SELECT user_id FROM user_role WHERE role = 'ADMIN'", Long.class)) {
        userRepository.findById(id).ifPresent(a -> {
          notificationService.sendToUser(a, false, text);
          notificationService.sendToUser(a, true, text);
        });
      }
    } catch (Exception e) {
      log.warn("Aviso de SOS aos administradores falhou: {}", e.getMessage());
    }
  }

  private static Double dbl(Object o) {
    try { return o == null ? null : Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
  }
}
