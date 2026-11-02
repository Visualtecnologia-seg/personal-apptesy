package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.OpiBot;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final OpiBot opi;
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String OPI = "Opi · assistente OPUS";

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
    String action = Objects.toString(body.get("action"), "");
    if (text.isEmpty() && action.isEmpty()) return bad("Escreva a sua mensagem.");
    if (text.isEmpty()) text = "human".equals(action) ? "Quero falar com um atendente" : "Resolveu, obrigado!";
    boolean pro = SocialController.hasRole(me, Role.PROFESSIONAL) && !SocialController.hasRole(me, Role.CUSTOMER) || "pro".equals(body.get("app"));
    Map<String, Object> st = state(me.getId());
    boolean human = Boolean.TRUE.equals(st.get("human"));
    LocalDateTime now = LocalDateTime.now();
    if (human && !"ok".equals(action)) {
      // A equipe já está cuidando: a mensagem vai direto para o painel.
      jdbc.update("INSERT INTO support_message_ (user_id, from_admin, text, created_at, read_by_user, read_by_admin) VALUES (?, FALSE, ?, ?, TRUE, FALSE)", me.getId(), text, Timestamp.valueOf(now));
      return ResponseEntity.ok(messages(me.getId()));
    }
    int misses = ((Number) st.getOrDefault("misses", 0)).intValue();
    OpiBot.Reply r = opi.reply(text, action, firstName(me), pro, misses);
    boolean escalate = r.escalate;
    // mensagem da pessoa: só fica "não lida" no painel se for para a equipe
    jdbc.update("INSERT INTO support_message_ (user_id, from_admin, text, created_at, read_by_user, read_by_admin) VALUES (?, FALSE, ?, ?, TRUE, ?)",
        me.getId(), text, Timestamp.valueOf(now), !escalate);
    String buttons = null;
    try { buttons = r.buttons.isEmpty() ? null : JSON.writeValueAsString(r.buttons); } catch (Exception ignored) { }
    jdbc.update("INSERT INTO support_message_ (user_id, from_admin, admin_name, text, created_at, read_by_user, read_by_admin, bot, buttons) VALUES (?, TRUE, ?, ?, ?, TRUE, TRUE, TRUE, ?)",
        me.getId(), OPI, r.text, Timestamp.valueOf(now.plusNanos(1_000_000)), buttons);
    boolean resolved = "ok".equals(action) || "obrigado".equals(r.topic);
    jdbc.update("UPDATE support_state_ SET human = ?, priority = ?, misses = ?, topic = ?, updated_at = ? WHERE user_id = ?",
        escalate, escalate && (r.priority || Boolean.TRUE.equals(st.get("priority"))) && !resolved, "nao-entendi".equals(r.topic) ? misses + 1 : 0, r.topic, Timestamp.valueOf(now), me.getId());
    if (escalate) log.info("Opi passou o suporte de {} para a equipe ({}{})", me.getId(), r.topic, r.priority ? ", PRIORIDADE" : "");
    return ResponseEntity.ok(messages(me.getId()));
  }

  /** Situação da conversa (a Opi responde ou a equipe assumiu). */
  private Map<String, Object> state(Long userId) {
    jdbc.update("INSERT INTO support_state_ (user_id, human, priority, misses, updated_at) VALUES (?, FALSE, FALSE, 0, ?) ON CONFLICT (user_id) DO NOTHING", userId, Timestamp.valueOf(LocalDateTime.now()));
    return jdbc.queryForMap("SELECT human, priority, misses FROM support_state_ WHERE user_id = ?", userId);
  }

  // ───────────── Painel ─────────────

  @GetMapping("/admin/support/threads")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public List<Map<String, Object>> threads() {
    return jdbc.queryForList(
        "SELECT s.user_id AS \"userId\", u.name, u.username, u.email, COALESCE(st.human, FALSE) AS \"needsHuman\", COALESCE(st.priority, FALSE) AS priority, st.topic, " +
            "MAX(s.created_at) AS \"lastAt\", " +
            "SUM(CASE WHEN s.from_admin = FALSE AND s.read_by_admin = FALSE THEN 1 ELSE 0 END) AS unread, " +
            "(SELECT t.text FROM support_message_ t WHERE t.user_id = s.user_id ORDER BY t.id DESC LIMIT 1) AS \"lastText\" " +
            "FROM support_message_ s JOIN user_ u ON u.id = s.user_id LEFT JOIN support_state_ st ON st.user_id = s.user_id " +
            "GROUP BY s.user_id, u.name, u.username, u.email, st.human, st.priority, st.topic ORDER BY COALESCE(st.priority, FALSE) DESC, COALESCE(st.human, FALSE) DESC, MAX(s.created_at) DESC LIMIT 200");
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
    out.put("state", state(userId));
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
        userId, "Equipe OPUS", text, Timestamp.valueOf(LocalDateTime.now()));
    state(userId);
    jdbc.update("UPDATE support_state_ SET human = TRUE, priority = FALSE, updated_at = ? WHERE user_id = ?", Timestamp.valueOf(LocalDateTime.now()), userId);
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

  /** Encerra o atendimento humano: a Opi volta a responder essa pessoa. */
  @PostMapping("/admin/support/{userId}/release")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> release(@PathVariable Long userId) {
    state(userId);
    jdbc.update("UPDATE support_state_ SET human = FALSE, priority = FALSE, misses = 0, updated_at = ? WHERE user_id = ?", Timestamp.valueOf(LocalDateTime.now()), userId);
    jdbc.update("UPDATE support_message_ SET read_by_admin = TRUE WHERE user_id = ? AND from_admin = FALSE", userId);
    return Map.of("ok", true);
  }

  /** Assuntos que a Opi conhece (para o painel). */
  @GetMapping("/admin/support/opi")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> opiTopics() {
    return Map.of("cliente", opi.topics(false).stream().map(t -> Map.of("id", t.id, "kind", t.kind)).toArray(),
        "profissional", opi.topics(true).stream().map(t -> Map.of("id", t.id, "kind", t.kind)).toArray());
  }

  // ───────────── utilitários ─────────────

  private List<Map<String, Object>> messages(Long userId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, from_admin AS \"fromAdmin\", admin_name AS \"adminName\", text, created_at AS \"createdAt\", COALESCE(bot, FALSE) AS bot, buttons FROM support_message_ WHERE user_id = ? ORDER BY id",
        userId);
    for (Map<String, Object> m : rows) {
      Object b = m.remove("buttons");
      if (b != null) try { m.put("buttons", JSON.readValue(b.toString(), List.class)); } catch (Exception ignored) { }
    }
    return rows;
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
