package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/** Chat de suporte dentro do app, respondido pela equipe no painel. */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SupportController {

  private final UserService userService;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  // ───────────── Usuário (apps) ─────────────

  @GetMapping("/support/messages")
  public List<Map<String, Object>> mine() {
    User me = userService.findAuthUser();
    jdbc.update("UPDATE support_message_ SET read_by_user = TRUE WHERE user_id = ? AND from_admin = TRUE AND read_by_user = FALSE", me.getId());
    return messages(me.getId());
  }

  @GetMapping("/support/unread")
  public Map<String, Object> unread() {
    User me = userService.findAuthUser();
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM support_message_ WHERE user_id = ? AND from_admin = TRUE AND read_by_user = FALSE", Long.class, me.getId());
    return Map.of("unread", n == null ? 0 : n);
  }

  @PostMapping("/support/messages")
  public ResponseEntity<?> send(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    String text = clean(body.get("text"));
    if (text.isEmpty()) return bad("Escreva a sua mensagem.");
    boolean first = count(me.getId()) == 0;
    jdbc.update("INSERT INTO support_message_ (user_id, from_admin, text, created_at, read_by_user, read_by_admin) VALUES (?, FALSE, ?, ?, TRUE, FALSE)",
        me.getId(), text, Timestamp.valueOf(LocalDateTime.now()));
    if (first) {
      jdbc.update("INSERT INTO support_message_ (user_id, from_admin, admin_name, text, created_at, read_by_user, read_by_admin) VALUES (?, TRUE, 'Equipe OPUS', ?, ?, FALSE, TRUE)",
          me.getId(), "Oi, " + firstName(me) + "! Recebemos a sua mensagem. Nossa equipe responde por aqui, normalmente em poucas horas.",
          Timestamp.valueOf(LocalDateTime.now().plusSeconds(1)));
    }
    return ResponseEntity.ok(messages(me.getId()));
  }

  // ───────────── Painel ─────────────

  @GetMapping("/admin/support/threads")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> threads() {
    return jdbc.queryForList(
        "SELECT s.user_id AS \"userId\", u.name, u.username, u.email, " +
            "MAX(s.created_at) AS \"lastAt\", " +
            "SUM(CASE WHEN s.from_admin = FALSE AND s.read_by_admin = FALSE THEN 1 ELSE 0 END) AS unread, " +
            "(SELECT t.text FROM support_message_ t WHERE t.user_id = s.user_id ORDER BY t.id DESC LIMIT 1) AS \"lastText\" " +
            "FROM support_message_ s JOIN user_ u ON u.id = s.user_id GROUP BY s.user_id, u.name, u.username, u.email ORDER BY MAX(s.created_at) DESC LIMIT 200");
  }

  @GetMapping("/admin/support/{userId}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> thread(@PathVariable Long userId) {
    jdbc.update("UPDATE support_message_ SET read_by_admin = TRUE WHERE user_id = ? AND from_admin = FALSE", userId);
    Map<String, Object> out = new LinkedHashMap<>();
    User u = userService.findById(userId);
    out.put("userId", userId);
    out.put("name", u.getName());
    out.put("username", u.getUsername());
    out.put("email", u.getEmail());
    out.put("professional", SocialController.hasRole(u, Role.PROFESSIONAL));
    out.put("messages", messages(userId));
    out.put("orders", jdbc.queryForList(
        "SELECT id, status, date, total_cost AS \"totalCost\", dispute_status AS \"disputeStatus\" FROM order_ WHERE customer_id = ? OR professional_id = ? ORDER BY created_at DESC LIMIT 10",
        userId, userId));
    return out;
  }

  @PostMapping("/admin/support/{userId}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<?> reply(@PathVariable Long userId, @RequestBody Map<String, Object> body) {
    User admin = userService.findAuthUser();
    String text = clean(body.get("text"));
    if (text.isEmpty()) return bad("Escreva a resposta.");
    jdbc.update("INSERT INTO support_message_ (user_id, from_admin, admin_name, text, created_at, read_by_user, read_by_admin) VALUES (?, TRUE, ?, ?, ?, FALSE, TRUE)",
        userId, "Equipe OPUS · " + firstName(admin), text, Timestamp.valueOf(LocalDateTime.now()));
    try {
      User u = userService.findById(userId);
      String preview = text.length() > 80 ? text.substring(0, 80) + "…" : text;
      boolean pro = SocialController.hasRole(u, Role.PROFESSIONAL);
      notificationService.sendToUser(u, pro, "Suporte OPUS: " + preview);
    } catch (Exception e) {
      log.warn("Aviso de resposta do suporte não enviado: {}", e.getMessage());
    }
    return ResponseEntity.ok(messages(userId));
  }

  // ───────────── utilitários ─────────────

  private List<Map<String, Object>> messages(Long userId) {
    return jdbc.queryForList(
        "SELECT id, from_admin AS \"fromAdmin\", admin_name AS \"adminName\", text, created_at AS \"createdAt\" FROM support_message_ WHERE user_id = ? ORDER BY id",
        userId);
  }

  private long count(Long userId) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM support_message_ WHERE user_id = ?", Long.class, userId);
    return n == null ? 0 : n;
  }

  private static String clean(Object o) {
    String t = Objects.toString(o, "").trim();
    return t.length() > 2000 ? t.substring(0, 2000) : t;
  }
}
