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
    // O dinheiro não sai na hora: vai para a carteira depois do horário da aula + 2 h (dá tempo de o aluno reclamar)
    for (Long uid : paidStudents(id)) notify(uid, false, "Aula encerrada. Veja o resumo do seu treino no app e avalie " + firstName(me) + ".");
    return one(id);
  }

  /** O aluno relata problema na aula: o valor da vaga fica no cofre até a equipe OPUS decidir. */
  @PostMapping("/classes/{id}/problem")
  public ResponseEntity<?> problem(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    Map<String, Object> c = classRow(id);
    if (c == null) return bad("Aula não encontrada.");
    String reason = Objects.toString(b.get("reason"), "").trim();
    if (reason.length() < 5) return bad("Conte o que aconteceu.");
    if (reason.length() > 1000) reason = reason.substring(0, 1000);
    if (!List.of("LIVE", "DONE").contains(String.valueOf(c.get("status")))) return bad("A reclamação fica disponível quando a aula começa.");
    int n = jdbc.update("UPDATE class_enrollment_ SET dispute_status = 'OPEN', dispute_reason = ?, disputed_at = ? WHERE class_id = ? AND user_id = ? AND status = 'PAID' AND released = FALSE AND dispute_status IS NULL",
        reason, Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    if (n == 0) return bad("Não é possível reclamar desta aula agora (o valor já foi liberado ou já existe uma reclamação).");
    notify(((Number) c.get("professional_id")).longValue(), true, firstName(me) + " relatou um problema na aula \"" + c.get("title") + "\". A equipe OPUS vai analisar.");
    return one(id);
  }

  /** Decisão da equipe sobre a reclamação de uma vaga: libera ao professor ou devolve ao aluno. */
  @PostMapping("/admin/classes/enrollments/{eid}/resolve")
  public ResponseEntity<?> resolve(@PathVariable UUID eid, @RequestBody Map<String, Object> b) {
    String decision = Objects.toString(b.get("decision"), "");
    String note = Objects.toString(b.get("note"), "").trim();
    if (!"RELEASE".equals(decision) && !"REFUND".equals(decision)) return bad("Decisão inválida.");
    if (note.length() < 5) return bad("Escreva o motivo da decisão.");
    if (jdbc.update("UPDATE class_enrollment_ SET dispute_status = ?, dispute_note = ? WHERE id = ? AND dispute_status = 'OPEN' AND status = 'PAID' AND released = FALSE",
        "RELEASE".equals(decision) ? "RESOLVED_PRO" : "RESOLVED_CLIENT", note, eid) != 1) return bad("Esta reclamação não está aberta.");
    Map<String, Object> e = jdbc.queryForMap("SELECT e.class_id, e.user_id, e.service_value, e.pix_payment_id, g.professional_id, g.title FROM class_enrollment_ e JOIN group_class_ g ON g.id = e.class_id WHERE e.id = ?", eid);
    if ("RELEASE".equals(decision)) {
      if (jdbc.update("UPDATE class_enrollment_ SET released = TRUE WHERE id = ? AND released = FALSE", eid) == 1) {
        boolean test = asaasService.isTestMode() || String.valueOf(e.get("pix_payment_id")).startsWith("TESTE-");
        jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test) VALUES (?, ?, 'CLASS', ?, NULL, 'DONE', ?, ?, ?)",
            UUID.randomUUID(), e.get("professional_id"), e.get("service_value"), "Aula em grupo: " + e.get("title"), Timestamp.valueOf(LocalDateTime.now()), test);
      }
    } else {
      jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE id = ?", eid);
      refundEnrollment(eid);
    }
    notify(((Number) e.get("user_id")).longValue(), false, "A OPUS analisou a sua reclamação da aula \"" + e.get("title") + "\": " + ("RELEASE".equals(decision) ? "o valor foi liberado ao professor. " : "o valor foi devolvido para você. ") + note);
    notify(((Number) e.get("professional_id")).longValue(), true, "Reclamação da aula \"" + e.get("title") + "\": " + ("RELEASE".equals(decision) ? "o valor foi liberado para a sua carteira." : "o valor foi devolvido ao aluno. ") + " " + note);
    return ResponseEntity.ok(Map.of("ok", true));
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
    // Vaga sem pagar fica segura só por 15 minutos (antes contas falsas lotavam a turma sem pagar)
    long taken = count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND user_id <> ? AND (status = 'PAID' OR (status = 'PENDING' AND created_at > ?))",
        id, me.getId(), Timestamp.valueOf(LocalDateTime.now().minusMinutes(15)));
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
      // Voltou depois de desistir: o PIX antigo foi cancelado ou devolvido e não pode ser pago de novo, então gera outro
      if (!"PENDING".equals(ex.get(0).get("status"))) {
        forgetPix(eid);
        jdbc.update("UPDATE class_enrollment_ SET pix_payment_id = NULL, pix_copy_paste = NULL, pix_qr = NULL, paid_at = NULL WHERE id = ? AND COALESCE(released, FALSE) = FALSE", eid);
      } else {
        // Reserva de ontem: o PIX venceu. Cancela o antigo e gera outro.
        List<Map<String, Object>> old = jdbc.queryForList("SELECT pix_payment_id FROM class_enrollment_ WHERE id = ? AND pix_payment_id IS NOT NULL AND created_at < ?", eid, Timestamp.valueOf(LocalDateTime.now().minusHours(20)));
        if (!old.isEmpty()) {
          try { asaasService.cancelCharge((String) old.get(0).get("pix_payment_id")); } catch (Exception ignored) { /* se foi paga, o aviso do banco devolve */ }
          forgetPix(eid);
          jdbc.update("UPDATE class_enrollment_ SET pix_payment_id = NULL, pix_copy_paste = NULL, pix_qr = NULL WHERE id = ? AND status = 'PENDING'", eid);
        }
      }
      jdbc.update("UPDATE class_enrollment_ SET status = 'PENDING', service_value = ?, client_total = ?, refunded = FALSE, created_at = ? WHERE id = ?", service, total, Timestamp.valueOf(LocalDateTime.now()), eid);
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
    List<UUID> paidIds = jdbc.queryForList("SELECT id FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PAID'", UUID.class, id, me.getId());
    // Reserva sem pagar: cancela também a cobrança PIX, para a pessoa não pagar uma vaga que já desistiu
    for (String pix : jdbc.queryForList("SELECT pix_payment_id FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PENDING' AND pix_payment_id IS NOT NULL", String.class, id, me.getId())) {
      try { asaasService.cancelCharge(pix); } catch (Exception ignored) { /* se já foi paga, o aviso do banco devolve sozinho */ }
    }
    int n = jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE class_id = ? AND user_id = ? AND status IN ('PENDING','PAID')", id, me.getId());
    for (UUID e : paidIds) refundEnrollment(e);
    kickFromRoom(id, me.getId());
    return n > 0 ? one(id) : bad("Você não está inscrito nesta aula.");
  }

  /** Webhook do Asaas: a cobrança paga pode ser de uma vaga em aula. */
  public boolean markPaidByPixId(String pixId) {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM class_enrollment_ WHERE pix_payment_id = ? AND status = 'PENDING'", UUID.class, pixId);
    if (ids.isEmpty()) return false;
    markPaid(ids.get(0));
    return true;
  }

  /** Guarda o PIX trocado: se ele ainda for pago, o aviso do banco acha o registro e devolve o dinheiro. */
  private void forgetPix(UUID enrollmentId) {
    jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) SELECT user_id, 'PIX_SUBSTITUIDO', 'PIX ' || pix_payment_id, NOW() " +
        "FROM class_enrollment_ WHERE id = ? AND pix_payment_id IS NOT NULL", enrollmentId);
  }

  private static final Object PAY_LOCK = new Object();

  private void markPaid(UUID enrollmentId) {
    int n;
    synchronized (PAY_LOCK) {
      // Turma lotada ou encerrada enquanto o PIX não caía (a reserva sem pagar só segura a vaga por 15 min):
      // antes a vaga era confirmada mesmo assim e a turma passava do limite da sala. Agora o dinheiro volta.
      List<Map<String, Object>> cl = jdbc.queryForList("SELECT g.status, g.max_students, g.title, " +
          "(SELECT COUNT(*) FROM class_enrollment_ x WHERE x.class_id = g.id AND x.status = 'PAID') AS paid " +
          "FROM group_class_ g JOIN class_enrollment_ e ON e.class_id = g.id WHERE e.id = ?", enrollmentId);
      if (cl.isEmpty()) return;
      Map<String, Object> g = cl.get(0);
      boolean open = List.of("OPEN", "CONFIRMED").contains(String.valueOf(g.get("status")));
      if (!open || ((Number) g.get("paid")).intValue() >= ((Number) g.get("max_students")).intValue()) {
        if (jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'", enrollmentId) == 1) {
          refundEnrollment(enrollmentId);
          Long uid = jdbc.queryForObject("SELECT user_id FROM class_enrollment_ WHERE id = ?", Long.class, enrollmentId);
          notify(uid, false, (open ? "A turma de \"" + g.get("title") + "\" lotou" : "As inscrições de \"" + g.get("title") + "\" fecharam")
              + " antes do seu PIX cair. O valor foi devolvido para a sua conta.");
        }
        return;
      }
      n = jdbc.update("UPDATE class_enrollment_ SET status = 'PAID', paid_at = ? WHERE id = ? AND status = 'PENDING'", Timestamp.valueOf(LocalDateTime.now()), enrollmentId);
    }
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
    // Só o professor e quem pagou a vaga veem a sala (lista de alunos e batimentos)
    Map<String, Object> c = classRow(id);
    if (c == null) return bad("Aula não encontrada.");
    boolean host = me.getId().equals(((Number) c.get("professional_id")).longValue());
    if (!host && count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PAID'", id, me.getId()) == 0) return bad("Garanta a sua vaga para entrar na sala.");
    jdbc.update("UPDATE room_member_ SET last_seen = ? WHERE class_id = ? AND user_id = ?", Timestamp.valueOf(LocalDateTime.now()), id, me.getId());
    return ResponseEntity.ok(room(id, me, after));
  }

  @PostMapping("/classes/{id}/room/signal")
  public ResponseEntity<?> signal(@PathVariable UUID id, @RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    if (count("SELECT COUNT(*) FROM room_member_ WHERE class_id = ? AND user_id = ?", id, me.getId()) == 0) return bad("Você não está na sala.");
    // Vaga desistida/devolvida não manda mais sinal; e no máximo 240 sinais por minuto (antes dava para encher o banco)
    Map<String, Object> cr = classRow(id);
    boolean hostSig = cr != null && me.getId().equals(((Number) cr.get("professional_id")).longValue());
    if (!hostSig && count("SELECT COUNT(*) FROM class_enrollment_ WHERE class_id = ? AND user_id = ? AND status = 'PAID'", id, me.getId()) == 0) return bad("Garanta a sua vaga para entrar na sala.");
    if (!br.com.personalfighters.util.RateLimit.allow("roomsig:" + id + ":" + me.getId(), 240, 60)) return ResponseEntity.status(429).body(Map.of("error", "Muitos sinais."));
    String kind = Objects.toString(b.get("kind"), "");
    if (!List.of("offer", "answer", "ice", "bye", "mute").contains(kind)) return bad("Sinal inválido.");
    long to;
    try { to = Long.parseLong(Objects.toString(b.get("to"), "")); } catch (Exception e) { return bad("Destino inválido."); }
    String payload = Objects.toString(b.get("payload"), "");
    if (payload.length() > 100_000) return bad("Sinal grande demais.");
    if (count("SELECT COUNT(*) FROM room_member_ WHERE class_id = ? AND user_id = ?", id, to) == 0) return bad("Essa pessoa não está na sala.");
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
      }
      // Aula encerrada há tempo suficiente: libera para a carteira do professor
      for (UUID id : jdbc.queryForList("SELECT id FROM group_class_ WHERE status = 'DONE' AND GREATEST(COALESCE(ended_at, starts_at), starts_at + (duration_min * interval '1 minute')) < ?",
          UUID.class, Timestamp.valueOf(LocalDateTime.now().minusHours(CLASS_HOLD_HOURS)))) {
        release(id, null);
      }
      jdbc.update("DELETE FROM room_signal_ WHERE created_at < ?", Timestamp.valueOf(LocalDateTime.now().minusHours(6)));
      jdbc.update("DELETE FROM call_signal_ WHERE created_at < ?", Timestamp.valueOf(LocalDateTime.now().minusHours(6))); // sinais de chamada também (antes ficavam para sempre)
    } catch (Exception e) {
      log.warn("Rotina das aulas em grupo falhou: {}", e.getMessage());
    }
  }

  private void cancelClass(UUID id, String why) {
    Map<String, Object> c = classRow(id);
    jdbc.update("UPDATE group_class_ SET status = 'CANCELLED' WHERE id = ?", id);
    List<Long> paid = paidStudents(id);
    List<UUID> paidIds = jdbc.queryForList("SELECT id FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID'", UUID.class, id);
    jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE class_id = ? AND status = 'PAID'", id);
    for (UUID e : paidIds) refundEnrollment(e);
    jdbc.update("UPDATE class_enrollment_ SET status = 'CANCELLED' WHERE class_id = ? AND status = 'PENDING'", id);
    for (Long uid : paid) notify(uid, false, why + ": \"" + c.get("title") + "\" foi cancelada e o seu dinheiro será devolvido.");
    notify(((Number) c.get("professional_id")).longValue(), true, why + ": \"" + c.get("title") + "\" foi cancelada.");
  }

  /** Tira o aluno da sala ao vivo e avisa o professor para cortar o vídeo dele (o vídeo vai direto entre os celulares). */
  private void kickFromRoom(UUID classId, Long userId) {
    Map<String, Object> c = classRow(classId);
    jdbc.update("DELETE FROM room_member_ WHERE class_id = ? AND user_id = ?", classId, userId);
    if (c != null && c.get("professional_id") != null)
      jdbc.update("INSERT INTO room_signal_ (class_id, from_user, to_user, kind, payload, created_at) VALUES (?, ?, ?, 'bye', '', ?)",
          classId, userId, ((Number) c.get("professional_id")).longValue(), Timestamp.valueOf(LocalDateTime.now()));
  }

  /** Devolve de verdade o PIX da vaga (antes só marcava "devolvido" e o dinheiro não voltava). Uma vez só por vaga. */
  private void refundEnrollment(UUID enrollmentId) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT pix_payment_id, user_id FROM class_enrollment_ WHERE id = ? AND COALESCE(refunded, FALSE) = FALSE AND COALESCE(released, FALSE) = FALSE", enrollmentId);
    if (r.isEmpty()) return;
    if (jdbc.update("UPDATE class_enrollment_ SET refunded = TRUE WHERE id = ? AND COALESCE(refunded, FALSE) = FALSE", enrollmentId) != 1) return;
    String pid = Objects.toString(r.get(0).get("pix_payment_id"), "");
    String err = pid.isEmpty() ? null : asaasService.refundPix(pid, null);
    if (err != null) {
      jdbc.update("UPDATE class_enrollment_ SET refunded = FALSE WHERE id = ?", enrollmentId);
      jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'DEVOLUCAO_PENDENTE', ?, NOW())", r.get(0).get("user_id"),
          "Vaga de aula " + enrollmentId.toString().substring(0, 8) + ": devolução do PIX não foi feita (" + err + "). Devolver pelo painel do Asaas.");
    }
  }

  /** Horas depois do fim da aula até o dinheiro ir para a carteira do professor. */
  static final int CLASS_HOLD_HOURS = 2;

  /**
   * Libera cada vaga paga uma única vez, para a CARTEIRA do professor (com as proteções de saque).
   * Antes era PIX direto na hora de "encerrar": encerrando várias vezes juntas, pagava em dobro.
   */
  private void release(UUID classId, User pro) {
    Map<String, Object> c = classRow(classId);
    Long proId = pro != null ? pro.getId() : ((Number) c.get("professional_id")).longValue();
    // Aula encerrada antes da metade do tempo: nada é liberado sozinho, a equipe confere (antes era só "começar" e "encerrar")
    if (pro == null && c.get("started_at") != null && c.get("ended_at") != null) {
      long mins = java.time.Duration.between(((Timestamp) c.get("started_at")).toLocalDateTime(), ((Timestamp) c.get("ended_at")).toLocalDateTime()).toMinutes();
      if (mins * 2 < ((Number) c.get("duration_min")).longValue())
        jdbc.update("UPDATE class_enrollment_ SET dispute_status = 'OPEN', disputed_at = ?, dispute_reason = ? WHERE class_id = ? AND status = 'PAID' AND released = FALSE AND dispute_status IS NULL",
            Timestamp.valueOf(LocalDateTime.now()), "Aula encerrada com " + mins + " de " + c.get("duration_min") + " minutos (conferência automática)", classId);
    }
    for (Map<String, Object> e : jdbc.queryForList("SELECT id, service_value, pix_payment_id FROM class_enrollment_ WHERE class_id = ? AND status = 'PAID' AND released = FALSE AND (dispute_status IS NULL OR dispute_status = 'RESOLVED_PRO')", classId)) {
      // trava atômica: só quem virar "released" de FALSE para TRUE credita
      if (jdbc.update("UPDATE class_enrollment_ SET released = TRUE WHERE id = ? AND released = FALSE", e.get("id")) != 1) continue;
      boolean test = asaasService.isTestMode() || String.valueOf(e.get("pix_payment_id")).startsWith("TESTE-");
      jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test) VALUES (?, ?, 'CLASS', ?, NULL, 'DONE', ?, ?, ?)",
          UUID.randomUUID(), proId, e.get("service_value"), "Aula em grupo: " + c.get("title"), Timestamp.valueOf(LocalDateTime.now()), test);
    }
  }

  // ───────────── utilitários ─────────────

  private String select() {
    return "SELECT c.id, c.professional_id AS \"professionalId\", u.name AS \"professionalName\", u.avatar_url AS \"professionalAvatar\", c.title, c.modality, c.description, c.level, " +
        "c.starts_at AS \"startsAt\", c.duration_min AS \"durationMin\", c.price, c.min_students AS \"minStudents\", c.max_students AS \"maxStudents\", c.status, " +
        "(SELECT COUNT(*) FROM class_enrollment_ e WHERE e.class_id = c.id AND e.status = 'PAID') AS paid, " +
        "(SELECT COUNT(*) FROM class_enrollment_ e WHERE e.class_id = c.id AND (e.status = 'PAID' OR (e.status = 'PENDING' AND e.created_at > NOW() - interval '15 minutes'))) AS reserved " +
        "FROM group_class_ c JOIN user_ u ON u.id = c.professional_id ";
  }

  private Map<String, Object> decorate(Map<String, Object> r, User me) {
    r.put("clientPrice", Pricing.clientTotal((BigDecimal) r.get("price")));
    r.put("isHost", me.getId().equals(((Number) r.get("professionalId")).longValue()));
    List<Map<String, Object>> e = jdbc.queryForList("SELECT id, status, pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\", client_total AS \"clientTotal\", dispute_status AS \"disputeStatus\", released FROM class_enrollment_ WHERE class_id = ? AND user_id = ?",
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
