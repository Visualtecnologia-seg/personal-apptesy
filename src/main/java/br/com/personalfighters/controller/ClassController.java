package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/**
 * Aulas em grupo (R1–R3): o profissional cria a turma, cada aluno paga a sua vaga (fica no cofre),
 * se não atingir o mínimo a aula é cancelada e o dinheiro devolvido; ao encerrar, o pagamento é liberado.
 * A sala ao vivo liga os celulares diretamente (até 8 pessoas), com batimentos de cada aluno.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ClassController {

  public static final int ROOM_LIMIT = 8;
  private static final int MIN_DECISION_HOURS = 2;

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserRepository userRepository;
  private final AsaasService asaasService;
  private final NotificationService notificationService;
  private final HealthController healthController;

  // ───────────── Lista e detalhes ─────────────

  @GetMapping("/classes")
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "") String modality, @RequestParam(required = false) Long professionalId) {
    User me = userService.findAuthUser();
    List<Object> args = new ArrayList<>();
    String where = "WHERE c.status IN ('OPEN','CONFIRMED','LIVE') AND c.starts_at > ?";
    args.add(Timestamp.valueOf(LocalDateTime.now().minusHours(3)));
    if (!modality.isBlank()) { where += " AND c.modality = ?"; args.add(modality); }
    if (professionalId != null) { where += " AND c.professional_id = ?"; args.add(professionalId); }
    List<Map<String, Object>> rows = jdbc.queryForList(select() + where + " ORDER BY c.starts_at LIMIT 100", args.toArray());
    rows.forEach(r -> decorate(r, me));
    return rows;
  }

  @GetMapping("/classes/mine")
  public List<Map<String, Object>> mine() {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList(select() + "WHERE c.professional_id = ? OR EXISTS (SELECT 1 FROM class_enrollment_ e WHERE e.class_id = c.id AND e.user_id = ? AND e.status IN ('PENDING','PAID')) " +
        "ORDER BY c.starts_at DESC LIMIT 60", me.getId(), me.getId());
    rows.forEach(r -> decorate(r, me));
    return rows;
  }

  @GetMapping("/classes/{id}")
  public ResponseEntity<?> one(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList(select() + "WHERE c.id = ?", id);
    if (rows.isEmpty()) return bad("Aula não encontrada.");
    Map<String, Object> c = decorate(rows.get(0), me);
    if (Boolean.TRUE.equals(c.get("isHost"))) {
      c.put("students", jdbc.queryForList("SELECT e.user_id AS \"userId\", u.name, e.status, e.paid_at AS \"paidAt\" FROM class_enrollment_ e JOIN user_ u ON u.id = e.user_id " +
          "WHERE e.class_id = ? AND e.status IN ('PENDING','PAID') ORDER BY e.created_at", id));
    }
    return ResponseEntity.ok(c);
  }

  // ───────────── Profissional cria e encerra ─────────────

  @PostMapping("/classes")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (!SocialController.hasRole(me, Role.PROFESSIONAL)) return bad("Só profissionais criam aulas.");
    String title = Objects.toString(b.get("title"), "").trim();
    String modality = Objects.toString(b.get("modality"), "").trim().toUpperCase();
    if (title.length() < 3) return bad("Dê um nome para a aula.");
    if (!List.of("IOGA", "ALONGAMENTO", "DANCA", "FUNCIONAL", "PILATES", "LUTA", "OUTRA").contains(modality)) modality = "OUTRA";
    LocalDateTime starts;
    try { starts = LocalDateTime.parse(Objects.toString(b.get("startsAt"), "")); } catch (Exception e) { return bad("Escolha a data e o horário."); }
    if (starts.isBefore(LocalDateTime.now().plusHours(MIN_DECISION_HOURS + 1))) return bad("Marque a aula com pelo menos 3 horas de antecedência.");
    int duration = intOf(b.get("durationMin"), 50);
    int min = Math.max(1, intOf(b.get("minStudents"), 3));
    int max = Math.min(ROOM_LIMIT - 1, Math.max(min, intOf(b.get("maxStudents"), ROOM_LIMIT - 1)));
    BigDecimal price;
    try { price = new BigDecimal(Objects.toString(b.get("price"), "0").replace(',', '.')).setScale(2, java.math.RoundingMode.HALF_UP); } catch (Exception e) { return bad("Preço inválido."); }
    if (price.compareTo(new BigDecimal("5")) < 0) return bad("O preço mínimo por aluno é R$ 5.");
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO group_class_ (id, professional_id, title, modality, description, level, starts_at, duration_min, price, min_students, max_students, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)",
        id, me.getId(), title, modality, Objects.toString(b.get("description"), ""), Objects.toString(b.get("level"), "Todos os níveis"),
        Timestamp.valueOf(starts), Math.max(15, Math.min(180, duration)), price, min, max, Timestamp.valueOf(LocalDateTime.now()));
    return one(id);
  }

  @PostMapping("/classes/{id}/start")
  public ResponseEntity<?> startClass(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null || !me.getId().equals(((Number) c.get("professional_id")).longValue())) return bad("Aula não encontrada.");
    if ("CANCELLED".equals(c.get("status")) || "DONE".equals(c.get("status"))) return bad("Esta aula já terminou.");
    LocalDateTime starts = ((Timestamp) c.get("starts_at")).toLocalDateTime();
    if (LocalDateTime.now().isBefore(starts.minusMinutes(15))) return bad("A sala abre 15 minutos antes do horário.");
    jdbc.update("UPDATE group_class_ SET status = 'LIVE', started_at = COALESCE(started_at, ?) WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), id);
    for (Long uid : paidStudents(id)) notify(uid, false, "Sua aula \"" + c.get("title") + "\" começou! Entre na sala pelo app.");
    return one(id);
  }

  @PostMapping("/classes/{id}/end")
  public ResponseEntity<?> endClass(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null || !me.getId().equals(((Number) c.get("professional_id")).longValue())) return bad("Aula não encontrada.");
    if (!"LIVE".equals(c.get("status"))) return bad("A aula não está ao vivo.");
    jdbc.update("UPDATE group_class_ SET status = 'DONE', ended_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), id);
    jdbc.update("DELETE FROM room_member_ WHERE class_id = ?", id);
    release(id, me);
    for (Long uid : paidStudents(id)) notify(uid, false, "Aula encerrada. Veja o resumo do seu treino no app e avalie " + firstName(me) + ".");
    return one(id);
  }

  @PostMapping("/classes/{id}/cancel")
  public ResponseEntity<?> cancelByPro(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null || !me.getId().equals(((Number) c.get("professional_id")).longValue())) return bad("Aula não encontrada.");
    if (!List.of("OPEN", "CONFIRMED").contains(String.valueOf(c.get("status")))) return bad("Esta aula não pode mais ser cancelada.");
    cancelClass(id, "O profissional cancelou a aula");
    return one(id);
  }

  // ───────────── Aluno entra e paga ─────────────

  @PostMapping("/classes/{id}/enroll")
  public ResponseEntity<?> enroll(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null) return bad("Aula não encontrada.");
    if (!List.of("OPEN", "CONFIRMED").contains(String.valueOf(c.get("status")))) return bad("As inscrições desta aula estão fechadas.");
    if (me.getId().equals(((Number) c.get("professional_id")).longValue())) return bad("Você é o professor desta aula.");
    long taken = count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND status IN ('PENDING','PAID') AND user_id <> ?", id, me.getId());
    if (taken >= ((Number) c.get("max_students")).intValue()) return bad("A turma está cheia.");
    BigDecimal service = (BigDecimal) c.get("price");
    BigDecimal total = Pricing.clientTotal(service);
    List<Map<String, Object>> ex = jdbc.queryForList("SELECT id, status FROM class_enrollment_ WHERE class_id = ? AND user_id = ?", id, me.getId());
    UUID eid;
    if (ex.isEmpty()) {
      eid = UUID.randomUUID();
      jdbc.update("INSERT INTO class_enrollment_ (id, class_id, user_id, status, service_value, client_total, created_at) VALUES (?, ?, ?, 'PENDING', ?, ?, ?)",
          eid, id, me.getId(), service, total, Timestamp.valueOf(LocalDateTime.now()));
    } else {
      eid = (UUID) ex.get(0).get("id");
      if ("PAID".equals(ex.get(0).get("status"))) return ResponseEntity.ok(enrollment(eid));
      jdbc.update("UPDATE class_enrollment_ SET status = 'PENDING', service_value = ?, client_total = ?, refunded = FALSE WHERE id = ?", service, total, eid);
    }
    Map<String, Object> e = jdbc.queryForMap("SELECT pix_payment_id FROM class_enrollment_ WHERE id = ?", eid);
    if (e.get("pix_payment_id") == null) {
      Map<String, String> ch = asaasService.createExtraCharge(me, total, "Aula em grupo Opus: " + c.get("title"));
      jdbc.update("UPDATE class_enrollment_ SET pix_payment_id = ?, pix_copy_paste = ?, pix_qr = ? WHERE id = ?", ch.get("id"), ch.get("payload"), ch.get("encodedImage"), eid);
    }
    Map<String, Object> out = enrollment(eid);
    out.put("testMode", asaasService.isTestMode());
    return ResponseEntity.ok(out);
  }

  @PostMapping("/classes/{id}/simulate-pay")
  public ResponseEntity<?> simulate(@PathVariable UUID id) {
    if (!asaasService.isTestMode()) return bad("O pagamento de teste está desligado.");
    User me = userService.findAuthUser();
    List<UUID> e = jdbc.queryForList("SELECT id FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PENDING'", UUID.class, id, me.getId());
    if (e.isEmpty()) return bad("Inscrição não encontrada.");
    markPaid(e.get(0));
    return one(id);
  }

  @PostMapping("/classes/{id}/leave")
  public ResponseEntity<?> leave(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null) return bad("Aula não encontrada.");
    LocalDateTime starts = ((Timestamp) c.get("starts_at")).toLocalDateTime();
    if (LocalDateTime.now().isAfter(starts.minusHours(MIN_DECISION_HOURS))) return bad("Faltam menos de 2 horas: a vaga não pode mais ser cancelada.");
    int n = jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED', refunded = (status = 'PAID') WHERE class_id = ? AND user_id = ? AND status IN ('PENDING','PAID')", id, me.getId());
    return n > 0 ? one(id) : bad("Você não está inscrito nesta aula.");
  }

  /** Webhook do Asaas: a cobrança paga pode ser de uma vaga em aula. */
  public boolean markPaidByPixId(String pixId) {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM class_enrollment_ WHERE pix_payment_id = ? AND status = 'PENDING'", UUID.class, pixId);
    if (ids.isEmpty()) return false;
    markPaid(ids.get(0));
    return true;
  }

  private void markPaid(UUID enrollmentId) {
    int n = jdbc.update("UPDATE class_enrollment_ SET status = 'PAID', paid_at = ? WHERE id = ? AND status = 'PENDING'", Timestamp.valueOf(LocalDateTime.now()), enrollmentId);
    if (n == 0) return;
    Map<String, Object> e = jdbc.queryForMap("SELECT e.class_id, u.name FROM class_enrollment_ e JOIN user_ u ON u.id = e.user_id WHERE e.id = ?", enrollmentId);
    UUID classId = (UUID) e.get("class_id");
    Map<String, Object> c = classRow(classId);
    long paid = count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID'", classId);
    notify(((Number) c.get("professional_id")).longValue(), true, firstName((String) e.get("name")) + " garantiu vaga em \"" + c.get("title") + "\" (" + paid + " aluno" + (paid > 1 ? "s" : "") + ").");
    if ("OPEN".equals(c.get("status")) && paid >= ((Number) c.get("min_students")).intValue()) {
      jdbc.update("UPDATE group_class_ SET status = 'CONFIRMED' WHERE id = ?", classId);
      for (Long uid : paidStudents(classId)) notify(uid, false, "A aula \"" + c.get("title") + "\" atingiu o mínimo de alunos e está confirmada!");
    }
  }

  // ───────────── Sala ao vivo ─────────────

  @PostMapping("/classes/{id}/room/join")
  public ResponseEntity<?> join(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null) return bad("Aula não encontrada.");
    boolean host = me.getId().equals(((Number) c.get("professional_id")).longValue());
    if (!host && count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PAID'", id, me.getId()) == 0) return bad("Garanta a sua vaga para entrar na sala.");
    if (!"LIVE".equals(c.get("status"))) return bad(host ? "Toque em \"Começar aula\" primeiro." : "A sala abre quando o profissional começar a aula.");
    jdbc.update("DELETE FROM room_member_ WHERE class_id = ? AND last_seen < ?", id, Timestamp.valueOf(LocalDateTime.now().minusSeconds(40)));
    long present = count("SELECT COUNT(*) FROM room_member_ WHERE class_id = ? AND user_id <> ?", id, me.getId());
    if (present >= ROOM_LIMIT - 1) return bad("A sala está cheia.");
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbc.update("INSERT INTO room_member_ (class_id, user_id, name, is_host, joined_at, last_seen) VALUES (?, ?, ?, ?, ?, ?) " +
        "ON CONFLICT (class_id, user_id) DO UPDATE SET joined_at = EXCLUDED.joined_at, last_seen = EXCLUDED.last_seen", id, me.getId(), firstName(me), host, now, now);
    jdbc.update("DELETE FROM room_signal_ WHERE class_id = ? AND (to_user = ? OR from_user = ?)", id, me.getId(), me.getId());
    return ResponseEntity.ok(room(id, me, 0));
  }

  @GetMapping("/classes/{id}/room")
  public ResponseEntity<?> poll(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
    User me = userService.findAuthUser();
    jdbc.update("UPDATE room_member_ SET last_seen = ? WHERE class_id = ? AND user_id = ?", Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    return ResponseEntity.ok(room(id, me, after));
  }

  @PostMapping("/classes/{id}/room/signal")
  public ResponseEntity<?> signal(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (count("SELECT COUNT(*) FROM room_member_ WHERE class_id = ? AND user_id = ?", id, me.getId()) == 0) return bad("Você não está na sala.");
    String kind = Objects.toString(b.get("kind"), "");
    if (!List.of("offer", "answer", "ice", "bye", "mute").contains(kind)) return bad("Sinal inválido.");
    long to;
    try { to = Long.parseLong(Objects.toString(b.get("to"), "")); } catch (Exception e) { return bad("Destino inválido."); }
    String payload = Objects.toString(b.get("payload"), "");
    if (payload.length() > 100_000) return bad("Sinal grande demais.");
    jdbc.update("INSERT INTO room_signal_ (class_id, from_user, to_user, kind, payload, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        id, me.getId(), to, kind, payload, Timestamp.valueOf(LocalDateTime.now()));
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/classes/{id}/room/bpm")
  public ResponseEntity<?> bpm(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    int bpm = intOf(b.get("bpm"), 0);
    if (bpm < 30 || bpm > 230) return bad("Batimento inválido.");
    jdbc.update("UPDATE room_member_ SET bpm = ?, bpm_at = ? WHERE class_id = ? AND user_id = ?", bpm, Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PostMapping("/classes/{id}/room/leave")
  public ResponseEntity<?> leaveRoom(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM room_member_ WHERE class_id = ? AND user_id = ?", id, me.getId());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  private Map<String, Object> room(UUID id, User me, long after) {
    Map<String, Object> c = classRow(id);
    boolean host = c != null && me.getId().equals(((Number) c.get("professional_id")).longValue());
    List<Map<String, Object>> members = jdbc.queryForList(
        "SELECT user_id AS \"userId\", name, is_host AS \"isHost\", joined_at AS \"joinedAt\", " +
            "CASE WHEN bpm_at > ? THEN bpm ELSE NULL END AS bpm FROM room_member_ WHERE class_id = ? AND last_seen > ? ORDER BY joined_at",
        Timestamp.valueOf(LocalDateTime.now().minusSeconds(45)), id, Timestamp.valueOf(LocalDateTime.now().minusSeconds(40)));
    if (host) {
      for (Map<String, Object> m : members) {
        if (Boolean.TRUE.equals(m.get("isHost"))) continue;
        Long uid = ((Number) m.get("userId")).longValue();
        Map<String, Object> h = healthController.forPro(uid, (String) m.get("name"));
        m.put("alerts", h.getOrDefault("alerts", List.of()));
        Object prof = h.get("profile");
        if (prof instanceof Map && ((Map<?, ?>) prof).get("birthYear") != null) {
          int age = LocalDate.now().getYear() - ((Number) ((Map<?, ?>) prof).get("birthYear")).intValue();
          m.put("hrMax", 220 - age);
        }
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", c == null ? "GONE" : c.get("status"));
    out.put("title", c == null ? "" : c.get("title"));
    out.put("me", me.getId());
    out.put("isHost", host);
    out.put("members", members);
    out.put("signals", jdbc.queryForList("SELECT id, from_user AS \"from\", kind, payload FROM room_signal_ WHERE class_id = ? AND to_user = ? AND id > ? ORDER BY id",
        id, me.getId(), after));
    return out;
  }

  // ───────────── Regras automáticas ─────────────

  /** A cada 5 minutos: cancela turmas que não atingiram o mínimo e fecha salas esquecidas. */
  @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
  public void tick() {
    try {
      Timestamp limit = Timestamp.valueOf(LocalDateTime.now().plusHours(MIN_DECISION_HOURS));
      for (Map<String, Object> c : jdbc.queryForList("SELECT id, title, min_students FROM group_class_ WHERE status = 'OPEN' AND starts_at <= ?", limit)) {
        UUID id = (UUID) c.get("id");
        long paid = count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID'", id);
        if (paid < ((Number) c.get("min_students")).intValue()) cancelClass(id, "A aula não atingiu o mínimo de alunos");
        else jdbc.update("UPDATE group_class_ SET status = 'CONFIRMED' WHERE id = ?", id);
      }
      for (Map<String, Object> c : jdbc.queryForList("SELECT id, professional_id FROM group_class_ WHERE status = 'LIVE' AND started_at < ? ",
          Timestamp.valueOf(LocalDateTime.now().minusHours(4)))) {
        UUID id = (UUID) c.get("id");
        jdbc.update("UPDATE group_class_ SET status = 'DONE', ended_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), id);
        release(id, null);
      }
      jdbc.update("DELETE FROM room_signal_ WHERE created_at < ?", Timestamp.valueOf(LocalDateTime.now().minusHours(6)));
    } catch (Exception e) {
      log.warn("Rotina das aulas em grupo falhou: {}", e.getMessage());
    }
  }

  private void cancelClass(UUID id, String why) {
    Map<String, Object> c = classRow(id);
    jdbc.update("UPDATE group_class_ SET status = 'CANCELLED' WHERE id = ?", id);
    List<Long> paid = paidStudents(id);
    jdbc.update("UPDATE class_enrollment_ SET refunded = TRUE, status = 'CANCELLED' WHERE class_id = ? AND status = 'PAID'", id);
    jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE class_id = ? AND status = 'PENDING'", id);
    for (Long uid : paid) notify(uid, false, why + ": \"" + c.get("title") + "\" foi cancelada e o seu dinheiro será devolvido.");
    notify(((Number) c.get("professional_id")).longValue(), true, why + ": \"" + c.get("title") + "\" foi cancelada.");
  }

  private void release(UUID classId, User pro) {
    Map<String, Object> c = classRow(classId);
    User professional = pro != null ? pro : userRepository.findById(((Number) c.get("professional_id")).longValue()).orElse(null);
    for (Map<String, Object> e : jdbc.queryForList("SELECT id, service_value FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID' AND released = FALSE", classId)) {
      if (asaasService.transferToProfessional(professional, (BigDecimal) e.get("service_value"), "Aula em grupo " + classId.toString().substring(0, 8))) {
        jdbc.update("UPDATE class_enrollment_ SET released = TRUE WHERE id = ?", e.get("id"));
      }
    }
  }

  // ───────────── utilitários ─────────────

  private String select() {
    return "SELECT c.id, c.professional_id AS \"professionalId\", u.name AS \"professionalName\", u.avatar_url AS \"professionalAvatar\", c.title, c.modality, c.description, c.level, " +
        "c.starts_at AS \"startsAt\", c.duration_min AS \"durationMin\", c.price, c.min_students AS \"minStudents\", c.max_students AS \"maxStudents\", c.status, " +
        "(SELECT COUNT(*) FROM class_enrollment_ e WHERE e.class_id = c.id AND e.status = 'PAID') AS paid, " +
        "(SELECT COUNT(*) FROM class_enrollment_ e WHERE e.class_id = c.id AND e.status IN ('PENDING','PAID')) AS reserved " +
        "FROM group_class_ c JOIN user_ u ON u.id = c.professional_id ";
  }

  private Map<String, Object> decorate(Map<String, Object> r, User me) {
    r.put("clientPrice", Pricing.clientTotal((BigDecimal) r.get("price")));
    r.put("isHost", me.getId().equals(((Number) r.get("professionalId")).longValue()));
    List<Map<String, Object>> e = jdbc.queryForList("SELECT id, status, pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\", client_total AS \"clientTotal\" FROM class_enrollment_ WHERE class_id = ? AND user_id = ?",
        r.get("id"), me.getId());
    r.put("myEnrollment", e.isEmpty() ? null : e.get(0));
    return r;
  }

  private Map<String, Object> enrollment(UUID id) {
    return new LinkedHashMap<>(jdbc.queryForMap("SELECT id, class_id AS \"classId\", status, client_total AS \"clientTotal\", pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\" FROM class_enrollment_ WHERE id = ?", id));
  }

  private Map<String, Object> classRow(UUID id) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT * FROM group_class_ WHERE id = ?", id);
    return r.isEmpty() ? null : r.get(0);
  }

  private List<Long> paidStudents(UUID id) {
    return jdbc.queryForList("SELECT user_id FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID'", Long.class, id);
  }

  private void notify(Long userId, boolean proApp, String text) {
    try { userRepository.findById(userId).ifPresent(u -> notificationService.sendToUser(u, proApp, text)); } catch (Exception ignored) { }
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  private static int intOf(Object o, int def) {
    try { return o == null ? def : (int) Math.round(Double.parseDouble(o.toString())); } catch (Exception e) { return def; }
  }
}
