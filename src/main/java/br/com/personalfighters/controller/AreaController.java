package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.AreaService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/** Telas T2, T7 e T8: área de atendimento, regiões por dia, modo viagem, horários e férias. */
@RestController
@RequiredArgsConstructor
public class AreaController {

  private final UserService userService;
  private final AreaService areaService;
  private final JdbcTemplate jdbc;

  @GetMapping("/pro/area")
  public ResponseEntity<?> mine() {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> a = jdbc.queryForList(
        "SELECT mode, center_lat AS \"centerLat\", center_lng AS \"centerLng\", center_label AS \"centerLabel\", radius_km AS \"radiusKm\", neighborhoods, " +
            "service_modes AS \"serviceModes\", place_address AS \"placeAddress\", place_lat AS \"placeLat\", place_lng AS \"placeLng\", weekly_goal AS \"weeklyGoal\" " +
            "FROM professional_area_ WHERE professional_id = ?", me.getId());
    out.put("area", a.isEmpty() ? null : a.get(0));
    out.put("days", jdbc.queryForList("SELECT id, weekday, label, lat, lng, radius_km AS \"radiusKm\" FROM professional_area_day_ WHERE professional_id = ? ORDER BY weekday", me.getId()));
    out.put("trips", jdbc.queryForList(
        "SELECT id, city, lat, lng, radius_km AS \"radiusKm\", start_date AS \"startDate\", end_date AS \"endDate\", hide_home AS \"hideHome\", note " +
            "FROM professional_trip_ WHERE professional_id = ? AND end_date >= ? ORDER BY start_date", me.getId(), Date.valueOf(AreaService.today())));
    out.put("hours", jdbc.queryForList("SELECT id, weekday, start_time AS \"start\", end_time AS \"end\" FROM professional_hours_ WHERE professional_id = ? ORDER BY weekday, start_time", me.getId()));
    out.put("blocks", jdbc.queryForList("SELECT id, start_date AS \"startDate\", end_date AS \"endDate\", reason FROM professional_block_ WHERE professional_id = ? AND end_date >= ? ORDER BY start_date", me.getId(), Date.valueOf(AreaService.today())));
    out.put("home", Map.of("lat", me.getLatitude() == null ? 0 : me.getLatitude(), "lng", me.getLongitude() == null ? 0 : me.getLongitude()));
    return ResponseEntity.ok(out);
  }

  @PutMapping("/pro/area")
  public ResponseEntity<?> save(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String mode = "NEIGHBORHOODS".equals(b.get("mode")) ? "NEIGHBORHOODS" : "RADIUS";
    double radius = Math.max(1, Math.min(200, AreaService.bd(b.get("radiusKm"), "10").doubleValue()));
    String modes = String.join(",", filterModes(b.get("serviceModes")));
    if (modes.isEmpty()) modes = "HOME";
    jdbc.update("INSERT INTO professional_area_ (professional_id, mode, center_lat, center_lng, center_label, radius_km, neighborhoods, service_modes, place_address, place_lat, place_lng, weekly_goal, updated_at) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (professional_id) DO UPDATE SET mode = EXCLUDED.mode, center_lat = EXCLUDED.center_lat, center_lng = EXCLUDED.center_lng, " +
            "center_label = EXCLUDED.center_label, radius_km = EXCLUDED.radius_km, neighborhoods = EXCLUDED.neighborhoods, service_modes = EXCLUDED.service_modes, " +
            "place_address = EXCLUDED.place_address, place_lat = EXCLUDED.place_lat, place_lng = EXCLUDED.place_lng, weekly_goal = COALESCE(EXCLUDED.weekly_goal, professional_area_.weekly_goal), updated_at = EXCLUDED.updated_at",
        me.getId(), mode, num(b.get("centerLat")), num(b.get("centerLng")), str(b.get("centerLabel"), 160), radius,
        str(b.get("neighborhoods"), 4000), modes, str(b.get("placeAddress"), 255), num(b.get("placeLat")), num(b.get("placeLng")),
        b.get("weeklyGoal") == null || Objects.toString(b.get("weeklyGoal")).isBlank() ? null : AreaService.bd(b.get("weeklyGoal"), "0"),
        Timestamp.valueOf(LocalDateTime.now()));
    return mine();
  }

  @PutMapping("/pro/area/days")
  public ResponseEntity<?> days(@RequestBody List<Map<String, Object>> list) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM professional_area_day_ WHERE professional_id = ?", me.getId());
    for (Map<String, Object> d : list) {
      int wd = (int) AreaService.bd(d.get("weekday"), "0").doubleValue();
      String label = str(d.get("label"), 160);
      if (wd < 1 || wd > 7 || label == null) continue;
      jdbc.update("INSERT INTO professional_area_day_ (id, professional_id, weekday, label, lat, lng, radius_km) VALUES (?,?,?,?,?,?,?)",
          UUID.randomUUID(), me.getId(), wd, label, num(d.get("lat")), num(d.get("lng")), AreaService.bd(d.get("radiusKm"), "10"));
    }
    return mine();
  }

  @PostMapping("/pro/trips")
  public ResponseEntity<?> addTrip(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String city = str(b.get("city"), 160);
    if (city == null) return bad("Diga para qual cidade você vai.");
    LocalDate start, end;
    try { start = LocalDate.parse(Objects.toString(b.get("startDate"))); end = LocalDate.parse(Objects.toString(b.get("endDate"))); }
    catch (Exception e) { return bad("Escolha as datas da viagem."); }
    if (end.isBefore(start)) return bad("A volta precisa ser depois da ida.");
    if (end.isBefore(AreaService.today())) return bad("Essas datas já passaram.");
    jdbc.update("INSERT INTO professional_trip_ (id, professional_id, city, lat, lng, radius_km, start_date, end_date, hide_home, note, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(), me.getId(), city, num(b.get("lat")), num(b.get("lng")), AreaService.bd(b.get("radiusKm"), "25"),
        Date.valueOf(start), Date.valueOf(end), !Boolean.FALSE.equals(b.get("hideHome")), str(b.get("note"), 200), Timestamp.valueOf(LocalDateTime.now()));
    return mine();
  }

  @DeleteMapping("/pro/trips/{id}")
  public ResponseEntity<?> delTrip(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM professional_trip_ WHERE id = ? AND professional_id = ?", id, me.getId());
    return mine();
  }

  @PutMapping("/pro/hours")
  public ResponseEntity<?> hours(@RequestBody List<Map<String, Object>> list) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM professional_hours_ WHERE professional_id = ?", me.getId());
    for (Map<String, Object> h : list) {
      try {
        int wd = (int) AreaService.bd(h.get("weekday"), "0").doubleValue();
        LocalTime s = LocalTime.parse(Objects.toString(h.get("start"))), e = LocalTime.parse(Objects.toString(h.get("end")));
        if (wd < 1 || wd > 7 || !e.isAfter(s)) continue;
        jdbc.update("INSERT INTO professional_hours_ (id, professional_id, weekday, start_time, end_time) VALUES (?,?,?,?,?)",
            UUID.randomUUID(), me.getId(), wd, Time.valueOf(s), Time.valueOf(e));
      } catch (Exception ignored) { }
    }
    return mine();
  }

  @PostMapping("/pro/blocks")
  public ResponseEntity<?> addBlock(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    try {
      LocalDate s = LocalDate.parse(Objects.toString(b.get("startDate"))), e = LocalDate.parse(Objects.toString(b.get("endDate")));
      if (e.isBefore(s)) return bad("A data final precisa ser depois da inicial.");
      jdbc.update("INSERT INTO professional_block_ (id, professional_id, start_date, end_date, reason) VALUES (?,?,?,?,?)",
          UUID.randomUUID(), me.getId(), Date.valueOf(s), Date.valueOf(e), str(b.get("reason"), 120));
    } catch (Exception ex) { return bad("Escolha as datas."); }
    return mine();
  }

  @DeleteMapping("/pro/blocks/{id}")
  public ResponseEntity<?> delBlock(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM professional_block_ WHERE id = ? AND professional_id = ?", id, me.getId());
    return mine();
  }

  /** O cliente vê onde o profissional atende, horários livres e se está de férias. */
  @GetMapping("/professionals/{id}/area")
  public ResponseEntity<?> publicArea(@PathVariable Long id) {
    Map<String, Object> out = areaService.summary(id);
    out.put("hours", jdbc.queryForList("SELECT weekday, start_time AS \"start\", end_time AS \"end\" FROM professional_hours_ WHERE professional_id = ? ORDER BY weekday, start_time", id));
    out.put("blocks", jdbc.queryForList("SELECT start_date AS \"startDate\", end_date AS \"endDate\" FROM professional_block_ WHERE professional_id = ? AND end_date >= ?", id, Date.valueOf(AreaService.today())));
    return ResponseEntity.ok(out);
  }

  private static List<String> filterModes(Object raw) {
    List<String> out = new ArrayList<>();
    if (raw instanceof List) for (Object o : (List<?>) raw) {
      String m = Objects.toString(o, "").toUpperCase(Locale.ROOT);
      if (Arrays.asList("HOME", "PRO_PLACE", "ONLINE").contains(m) && !out.contains(m)) out.add(m);
    }
    return out;
  }

  private static Double num(Object o) {
    if (o == null) return null;
    try { double v = Double.parseDouble(Objects.toString(o).replace(',', '.')); return v == 0 ? null : v; } catch (Exception e) { return null; }
  }

  private static String str(Object o, int max) {
    if (o == null) return null;
    String s = Objects.toString(o).trim();
    if (s.isEmpty()) return null;
    return s.length() > max ? s.substring(0, max) : s;
  }
}
