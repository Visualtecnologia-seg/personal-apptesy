package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/** Tela "Hoje" e "Agenda" do profissional + horários livres para o cliente marcar. */
@RestController
@RequiredArgsConstructor
public class ProDayController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final UserService userService;
  private final JdbcTemplate jdbc;

  @GetMapping("/pro/today")
  public ResponseEntity<?> today() {
    User me = userService.findAuthUser();
    Long id = me.getId();
    LocalDate d = LocalDate.now(Z);
    LocalDateTime dayStart = d.atStartOfDay();
    LocalDateTime weekStart = d.with(DayOfWeek.MONDAY).atStartOfDay();
    Map<String, Object> out = new LinkedHashMap<>();

    Boolean online = null;
    try {
      online = jdbc.queryForObject("SELECT p.is_online FROM user_ u JOIN professional_ p ON p.id = u.professional_id WHERE u.id = ?", Boolean.class, id);
    } catch (Exception ignored) { }
    out.put("online", Boolean.TRUE.equals(online));
    out.put("todayEarned", sum(id, dayStart, dayStart.plusDays(1)));
    out.put("weekEarned", sum(id, weekStart, weekStart.plusDays(7)));
    BigDecimal goal = null;
    try { goal = jdbc.queryForObject("SELECT weekly_goal FROM professional_area_ WHERE professional_id = ?", BigDecimal.class, id); } catch (Exception ignored) { }
    out.put("weeklyGoal", goal);

    // Próximos atendimentos (hoje e amanhã) e aulas em grupo de hoje
    out.put("next", jdbc.queryForList(
        "SELECT o.id, o.status, o.date, o.start_time AS \"startTime\", o.service_address AS \"address\", o.service_value AS \"serviceValue\", " +
            "o.service_mode AS \"serviceMode\", o.departed_at AS \"departedAt\", u.name AS \"clientName\", u.id AS \"clientId\", p.name AS \"service\", " +
            "a.latitude AS lat, a.longitude AS lng " +
            "FROM order_ o LEFT JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id LEFT JOIN address_ a ON a.id = o.address_id " +
            "WHERE o.professional_id = ? AND o.status IN ('CONFIRMED','AWAITING_PAYMENT','IN_PROGRESS','PENDING_CLIENT_CONFIRMATION') " +
            "AND (o.date IS NULL OR o.date >= ?) ORDER BY o.date NULLS LAST, o.start_time NULLS LAST LIMIT 6", id, Date.valueOf(d)));
    out.put("classes", jdbc.queryForList(
        "SELECT id, title, starts_at AS \"startsAt\", duration_min AS \"durationMin\", status, " +
            "(SELECT COUNT(*) FROM class_enrollment_ e WHERE e.class_id = g.id AND e.status IN ('PAID','CONFIRMED')) AS \"students\" " +
            "FROM group_class_ g WHERE professional_id = ? AND starts_at >= ? AND starts_at < ? AND status NOT IN ('CANCELLED') ORDER BY starts_at",
        id, Timestamp.valueOf(dayStart), Timestamp.valueOf(dayStart.plusDays(2))));
    out.put("monthDone", n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED') AND done_at >= ?", id, Timestamp.valueOf(d.withDayOfMonth(1).atStartOfDay())));
    long done = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id);
    out.put("done", done);
    out.put("level", done >= 100 ? "Ouro" : done >= 30 ? "Prata" : "Bronze");
    out.put("nextLevelAt", done >= 100 ? null : done >= 30 ? 100 : 30);
    return ResponseEntity.ok(out);
  }

  @PutMapping("/pro/goal")
  public ResponseEntity<?> goal(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    BigDecimal v;
    try { v = new BigDecimal(Objects.toString(b.get("value"), "0").replace(',', '.')); } catch (Exception e) { return bad("Valor inválido."); }
    if (v.signum() < 0 || v.compareTo(new BigDecimal("100000")) > 0) return bad("Valor inválido.");
    jdbc.update("INSERT INTO professional_area_ (professional_id, weekly_goal, updated_at) VALUES (?, ?, ?) " +
        "ON CONFLICT (professional_id) DO UPDATE SET weekly_goal = EXCLUDED.weekly_goal, updated_at = EXCLUDED.updated_at", me.getId(), v, Timestamp.valueOf(LocalDateTime.now(Z)));
    return ResponseEntity.ok(Map.of("weeklyGoal", v));
  }

  /** Agenda do profissional: pedidos, aulas, horários de atendimento e bloqueios no período. */
  @GetMapping("/pro/agenda")
  public ResponseEntity<?> agenda(@RequestParam(required = false) String from, @RequestParam(required = false) Integer days) {
    User me = userService.findAuthUser();
    LocalDate start = from == null ? LocalDate.now(Z) : LocalDate.parse(from);
    int n = days == null ? 7 : Math.max(1, Math.min(days, 31));
    LocalDate end = start.plusDays(n);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("from", start.toString());
    out.put("days", n);
    out.put("orders", jdbc.queryForList(
        "SELECT o.id, o.status, o.date, o.start_time AS \"startTime\", o.end_time AS \"endTime\", o.hours, o.service_value AS \"serviceValue\", " +
            "u.name AS \"clientName\", p.name AS \"service\", o.service_mode AS \"serviceMode\" " +
            "FROM order_ o LEFT JOIN user_ u ON u.id = o.customer_id LEFT JOIN product_ p ON p.id = o.product_id " +
            "WHERE o.professional_id = ? AND o.date >= ? AND o.date < ? AND o.status NOT IN ('CANCELLED','BLOCKED') ORDER BY o.date, o.start_time",
        me.getId(), Date.valueOf(start), Date.valueOf(end)));
    out.put("classes", jdbc.queryForList(
        "SELECT id, title, starts_at AS \"startsAt\", duration_min AS \"durationMin\", status FROM group_class_ " +
            "WHERE professional_id = ? AND starts_at >= ? AND starts_at < ? AND status NOT IN ('CANCELLED') ORDER BY starts_at",
        me.getId(), Timestamp.valueOf(start.atStartOfDay()), Timestamp.valueOf(end.atStartOfDay())));
    out.put("hours", jdbc.queryForList("SELECT id, weekday, start_time AS \"start\", end_time AS \"end\" FROM professional_hours_ WHERE professional_id = ? ORDER BY weekday, start_time", me.getId()));
    out.put("blocks", jdbc.queryForList("SELECT id, start_date AS \"startDate\", end_date AS \"endDate\", reason FROM professional_block_ WHERE professional_id = ? AND end_date >= ? AND start_date < ?",
        me.getId(), Date.valueOf(start), Date.valueOf(end)));
    return ResponseEntity.ok(out);
  }

  /** Horários livres de um profissional num dia (o cliente só marca nesses). Sem horários cadastrados: devolve vazio e o app deixa escolher livre. */
  @GetMapping("/professionals/{id}/free-slots")
  public ResponseEntity<?> freeSlots(@PathVariable Long id, @RequestParam String date, @RequestParam(defaultValue = "1") int hours) {
    LocalDate d;
    try { d = LocalDate.parse(date); } catch (Exception e) { return bad("Data inválida."); }
    int wd = d.getDayOfWeek().getValue();
    Map<String, Object> out = new LinkedHashMap<>();
    long blocked = n("SELECT COUNT(*) FROM professional_block_ WHERE professional_id = ? AND start_date <= ? AND end_date >= ?", id, Date.valueOf(d), Date.valueOf(d));
    List<Map<String, Object>> ranges = jdbc.queryForList("SELECT start_time, end_time FROM professional_hours_ WHERE professional_id = ? AND weekday = ? ORDER BY start_time", id, wd);
    long anyHours = n("SELECT COUNT(*) FROM professional_hours_ WHERE professional_id = ?", id);
    out.put("configured", anyHours > 0);
    out.put("blocked", blocked > 0);
    List<String> slots = new ArrayList<>();
    if (blocked == 0 && !ranges.isEmpty()) {
      List<Map<String, Object>> busy = jdbc.queryForList(
          "SELECT start_time, COALESCE(end_time, start_time + (COALESCE(hours,1) || ' hours')::interval) AS end_time FROM order_ " +
              "WHERE professional_id = ? AND date = ? AND start_time IS NOT NULL AND status NOT IN ('CANCELLED','BLOCKED','OPEN')", id, Date.valueOf(d));
      List<Map<String, Object>> classes = jdbc.queryForList(
          "SELECT starts_at, duration_min FROM group_class_ WHERE professional_id = ? AND starts_at >= ? AND starts_at < ? AND status NOT IN ('CANCELLED')",
          id, Timestamp.valueOf(d.atStartOfDay()), Timestamp.valueOf(d.plusDays(1).atStartOfDay()));
      LocalTime nowT = LocalDate.now(Z).equals(d) ? LocalTime.now(Z).plusMinutes(60) : LocalTime.MIN;
      int len = Math.max(1, Math.min(hours, 8)) * 60;
      for (Map<String, Object> r : ranges) {
        LocalTime a = ((Time) r.get("start_time")).toLocalTime();
        LocalTime b = ((Time) r.get("end_time")).toLocalTime();
        for (LocalTime t = a; !t.plusMinutes(len).isAfter(b) && t.plusMinutes(len).isAfter(t); t = t.plusMinutes(30)) {
          if (t.isBefore(nowT)) continue;
          LocalTime e = t.plusMinutes(len);
          boolean clash = false;
          for (Map<String, Object> o : busy) {
            LocalTime s = ((Time) o.get("start_time")).toLocalTime();
            Object eo = o.get("end_time");
            LocalTime f = eo instanceof Time ? ((Time) eo).toLocalTime() : s.plusHours(1);
            if (t.isBefore(f) && e.isAfter(s)) { clash = true; break; }
          }
          for (Map<String, Object> c : classes) {
            LocalTime s = ((Timestamp) c.get("starts_at")).toLocalDateTime().toLocalTime();
            LocalTime f = s.plusMinutes(((Number) c.get("duration_min")).intValue());
            if (t.isBefore(f) && e.isAfter(s)) { clash = true; break; }
          }
          if (!clash) slots.add(t.toString());
        }
      }
    }
    out.put("slots", slots);
    out.put("area", null);
    return ResponseEntity.ok(out);
  }

  private BigDecimal sum(Long pro, LocalDateTime a, LocalDateTime b) {
    BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND created_at >= ? AND created_at < ?",
        BigDecimal.class, pro, Timestamp.valueOf(a), Timestamp.valueOf(b));
    return v == null ? BigDecimal.ZERO : v;
  }

  private long n(String sql, Object... args) {
    Long v = jdbc.queryForObject(sql, Long.class, args);
    return v == null ? 0 : v;
  }

}
