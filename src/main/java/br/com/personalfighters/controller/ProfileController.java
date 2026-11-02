package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;

/** Perfil novo do profissional (visto pelo cliente), favoritos e seguir. */
@Slf4j
@RestController("proProfileController")
@RequiredArgsConstructor
public class ProfileController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final ProDayController proDay;
  private final NotificationService notificationService;

  private List<Map<String, Object>> q(String sql, Object... a) { try { return jdbc.queryForList(sql, a); } catch (Exception e) { log.debug("perfil: {}", e.getMessage()); return List.of(); } }
  private long n(String sql, Object... a) { try { Long v = jdbc.queryForObject(sql, Long.class, a); return v == null ? 0 : v; } catch (Exception e) { return 0; } }
  private Object one(String sql, Object... a) { List<Map<String, Object>> r = q(sql, a); return r.isEmpty() ? null : r.get(0).values().iterator().next(); }

  @GetMapping("/pros/{id}/profile")
  public ResponseEntity<?> profile(@PathVariable Long id, @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = q("SELECT u.id, u.name, u.avatar_url AS \"avatarUrl\", COALESCE(u.verified, FALSE) AS verified, u.latitude, u.longitude, u.created_at_ AS \"since\", " +
        "p.about, p.specialties, p.years_experience AS \"years\", COALESCE(p.hourly_rate, p.price, 0) AS rate, COALESCE(p.is_online, FALSE) AS online " +
        "FROM user_ u JOIN professional_ p ON p.id = u.professional_id WHERE u.id = ? AND u.username NOT LIKE 'painel:%'", id);
    if (rows.isEmpty()) return bad("Profissional não encontrado.");
    Map<String, Object> r = rows.get(0);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("name", r.get("name"));
    out.put("avatarUrl", r.get("avatarUrl"));
    out.put("verified", r.get("verified"));
    out.put("online", r.get("online"));
    out.put("since", r.get("since"));
    out.put("about", r.get("about"));
    out.put("years", r.get("years"));
    List<String> tags = new ArrayList<>();
    for (String t : Objects.toString(r.get("specialties"), "").split("[,;\\n]")) if (!t.isBlank()) tags.add(t.trim());
    out.put("specialties", tags);
    out.put("services", q("SELECT p.id, p.name FROM product__professional pp JOIN product_ p ON p.id = pp.product_id WHERE pp.professional_id = ? ORDER BY p.name", id));
    out.put("price", Math.round(((Number) r.get("rate")).doubleValue() * 1.05));
    long done = n("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND status IN ('DONE','REVIEWED')", id);
    out.put("done", done);
    out.put("level", done >= 100 ? "Ouro" : done >= 30 ? "Prata" : "Bronze");
    out.put("isNew", done == 0);
    Map<String, Object> rv = q("SELECT COUNT(*) AS n, ROUND(AVG(rating)::numeric,1) AS avg, ROUND(AVG(cat1)::numeric,1) AS c1, ROUND(AVG(cat2)::numeric,1) AS c2, ROUND(AVG(cat3)::numeric,1) AS c3, ROUND(AVG(cat4)::numeric,1) AS c4 " +
        "FROM review_ WHERE professional_id = ? AND rating IS NOT NULL AND COALESCE(type, 'CLIENT_TO_PRO') = 'CLIENT_TO_PRO'", id).stream().findFirst().orElse(Map.of());
    out.put("rating", rv.get("avg"));
    out.put("reviews", rv.getOrDefault("n", 0));
    List<Map<String, Object>> cats = new ArrayList<>();
    String[][] cl = {{"c1", "Qualidade"}, {"c2", "Pontualidade"}, {"c3", "Comunicação"}, {"c4", "Organização"}};
    for (String[] c : cl) if (rv.get(c[0]) != null) cats.add(Map.of("label", c[1], "value", rv.get(c[0])));
    out.put("ratingParts", cats);
    // Pontualidade: chegou até 10 min depois do horário marcado
    Object punct = one("SELECT ROUND(100.0 * SUM(CASE WHEN arrived_at <= (date + start_time + INTERVAL '10 minutes') THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0)) " +
        "FROM order_ WHERE professional_id = ? AND arrived_at IS NOT NULL AND date IS NOT NULL AND start_time IS NOT NULL", id);
    out.put("punctuality", punct);
    // Tempo médio para responder no chat (últimos 90 dias)
    Object resp = one("SELECT ROUND(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY mins)) FROM (" +
        "SELECT EXTRACT(EPOCH FROM (MIN(r.created_at) - c.created_at)) / 60 AS mins FROM message_ c JOIN order_ o ON o.id = c.order_id " +
        "JOIN message_ r ON r.order_id = c.order_id AND r.sender_role = 'PROFESSIONAL' AND r.created_at > c.created_at " +
        "WHERE o.professional_id = ? AND c.sender_role = 'CUSTOMER' AND c.created_at > NOW() - INTERVAL '90 days' GROUP BY c.id, c.created_at) x WHERE mins < 1440", id);
    out.put("responseMin", resp);
    Double km = SearchController.dist(lat, lng, r.get("latitude"), r.get("longitude"));
    out.put("distanceKm", km == null ? null : Math.round(km * 10) / 10.0);
    Map<String, Object> area = q("SELECT mode, radius_km AS \"radiusKm\", neighborhoods, service_modes AS \"modes\", center_lat AS lat, center_lng AS lng FROM professional_area_ WHERE professional_id = ?", id).stream().findFirst().orElse(null);
    if (area != null) {
      List<String> nb = new ArrayList<>();
      for (String s : Objects.toString(area.get("neighborhoods"), "").split("\\n")) if (!s.isBlank()) nb.add(s.trim());
      area.put("neighborhoods", nb);
      area.put("modes", Arrays.asList(Objects.toString(area.get("modes"), "HOME").split(",")));
    }
    out.put("area", area);
    out.put("warrantyDays", one("SELECT MAX(warranty_days) FROM order_ WHERE professional_id = ?", id));
    out.put("certificates", q("SELECT title FROM pro_certificate_ WHERE professional_id = ? AND status = 'APPROVED' ORDER BY created_at", id));
    out.put("slots", slots(id));
    out.put("favorite", n("SELECT COUNT(*) FROM favorite_ WHERE user_id = ? AND professional_id = ?", me.getId(), id) > 0);
    out.put("following", n("SELECT COUNT(*) FROM follow_ WHERE user_id = ? AND professional_id = ?", me.getId(), id) > 0);
    out.put("followers", n("SELECT COUNT(*) FROM follow_ WHERE professional_id = ?", id));
    out.put("posts", n("SELECT COUNT(*) FROM post_ WHERE professional_id = ?", id));
    out.put("likes", n("SELECT COUNT(*) FROM post_like_ l JOIN post_ p ON p.id = l.post_id WHERE p.professional_id = ?", id));
    out.put("lastReviews", q("SELECT r.id, r.rating, r.comment, r.date, c.name AS \"author\", rr.text AS \"reply\" FROM review_ r LEFT JOIN user_ c ON c.id = COALESCE(r.reviewer_id, r.customer_id) " +
        "LEFT JOIN review_reply_ rr ON rr.review_id = r.id WHERE r.professional_id = ? AND r.rating IS NOT NULL AND COALESCE(r.type, 'CLIENT_TO_PRO') = 'CLIENT_TO_PRO' ORDER BY r.id DESC LIMIT 5", id));
    return ResponseEntity.ok(out);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> slots(Long id) {
    List<Map<String, Object>> out = new ArrayList<>();
    LocalDate d0 = LocalDate.now(Z);
    for (int i = 0; i < 4 && out.size() < 3; i++) {
      LocalDate d = d0.plusDays(i);
      try {
        Object body = proDay.freeSlots(id, d.toString(), 1).getBody();
        if (!(body instanceof Map)) continue;
        Object s = ((Map<String, Object>) body).get("slots");
        if (!(s instanceof List)) continue;
        List<?> l = (List<?>) s;
        for (int k = 0; k < l.size() && out.size() < 3; k += Math.max(1, l.size() / 2)) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("date", d.toString());
          m.put("time", String.valueOf(l.get(k)));
          m.put("day", i == 0 ? "Hoje" : i == 1 ? "Amanhã" : d.getDayOfMonth() + "/" + d.getMonthValue());
          out.add(m);
        }
      } catch (Exception ignored) { }
    }
    return out;
  }

  // ───────────── Favoritos e seguir ─────────────

  @PostMapping("/pros/{id}/favorite")
  public Map<String, Object> favorite(@PathVariable Long id) {
    User me = userService.findAuthUser();
    boolean on = n("SELECT COUNT(*) FROM favorite_ WHERE user_id = ? AND professional_id = ?", me.getId(), id) == 0;
    if (on) jdbc.update("INSERT INTO favorite_ (user_id, professional_id, created_at) VALUES (?,?,?) ON CONFLICT DO NOTHING", me.getId(), id, Timestamp.valueOf(LocalDateTime.now(Z)));
    else jdbc.update("DELETE FROM favorite_ WHERE user_id = ? AND professional_id = ?", me.getId(), id);
    return Map.of("favorite", on);
  }

  @GetMapping("/me/favorites")
  public List<Map<String, Object>> favorites() {
    User me = userService.findAuthUser();
    return q("SELECT u.id, u.name, u.avatar_url AS \"avatarUrl\", COALESCE(u.verified, FALSE) AS verified, COALESCE(p.is_online, FALSE) AS online, p.specialties, " +
        "ROUND(COALESCE(p.hourly_rate, p.price, 0) * 1.05) AS price, (SELECT ROUND(AVG(r.rating)::numeric,1) FROM review_ r WHERE r.professional_id = u.id AND r.rating IS NOT NULL) AS rating " +
        "FROM favorite_ f JOIN user_ u ON u.id = f.professional_id JOIN professional_ p ON p.id = u.professional_id WHERE f.user_id = ? ORDER BY f.created_at DESC", me.getId());
  }

  @PostMapping("/pros/{id}/follow")
  public Map<String, Object> follow(@PathVariable Long id) {
    User me = userService.findAuthUser();
    boolean on = n("SELECT COUNT(*) FROM follow_ WHERE user_id = ? AND professional_id = ?", me.getId(), id) == 0;
    if (on) {
      jdbc.update("INSERT INTO follow_ (user_id, professional_id, created_at) VALUES (?,?,?) ON CONFLICT DO NOTHING", me.getId(), id, Timestamp.valueOf(LocalDateTime.now(Z)));
      try { userService.findById(id); notificationService.sendToUser(userService.findById(id), true, SocialController.firstName(me) + " começou a seguir você na OPUS."); } catch (Exception ignored) { }
    } else jdbc.update("DELETE FROM follow_ WHERE user_id = ? AND professional_id = ?", me.getId(), id);
    return Map.of("following", on, "followers", n("SELECT COUNT(*) FROM follow_ WHERE professional_id = ?", id));
  }

  // ───────────── O profissional edita o "Sobre" ─────────────

  @GetMapping("/pro/about")
  public Map<String, Object> myAbout() {
    User me = userService.findAuthUser();
    return q("SELECT p.about, p.specialties, p.years_experience AS \"years\" FROM professional_ p JOIN user_ u ON u.professional_id = p.id WHERE u.id = ?", me.getId())
        .stream().findFirst().orElse(Map.of());
  }

  @PutMapping("/pro/about")
  public ResponseEntity<?> saveAbout(@RequestBody Map<String, Object> b) {
    User me = userService.findAuthUser();
    String about = Objects.toString(b.get("about"), "").trim();
    if (about.length() > 600) about = about.substring(0, 600);
    String spec = Objects.toString(b.get("specialties"), "").trim();
    if (spec.length() > 480) spec = spec.substring(0, 480);
    Integer years = null;
    try { years = Integer.valueOf(Objects.toString(b.get("years"), "").replaceAll("\\D", "")); if (years > 70) years = 70; } catch (Exception ignored) { }
    int n = jdbc.update("UPDATE professional_ SET about = ?, specialties = ?, years_experience = ? WHERE id = (SELECT professional_id FROM user_ WHERE id = ?)",
        about.isEmpty() ? null : about, spec.isEmpty() ? null : spec, years, me.getId());
    if (n == 0) return bad("Só profissionais têm perfil.");
    return ResponseEntity.ok(myAbout());
  }
}
