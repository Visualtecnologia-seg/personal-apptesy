package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Onde cada profissional atende: raio/bairros, regiões por dia da semana e modo viagem. */
@Service
@RequiredArgsConstructor
public class AreaService {

  private final JdbcTemplate jdbc;

  public static LocalDate today() { return LocalDate.now(ZoneId.of("America/Sao_Paulo")); }

  public static double km(double lat1, double lon1, double lat2, double lon2) {
    double r = 6371.0, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  static String plain(String s) {
    if (s == null) return "";
    return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  private static double d(Object o, double def) {
    if (o instanceof Number) return ((Number) o).doubleValue();
    return def;
  }

  /**
   * O profissional atende neste ponto hoje?
   * Retorna null quando ele não configurou área (vale a busca normal por distância).
   */
  public Boolean covers(User pro, double lat, double lng, String neighborhood) {
    LocalDate day = today();
    List<Map<String, Object>> trips = jdbc.queryForList(
        "SELECT lat, lng, radius_km, hide_home FROM professional_trip_ WHERE professional_id = ? AND ? BETWEEN start_date AND end_date",
        pro.getId(), Date.valueOf(day));
    boolean hideHome = false;
    for (Map<String, Object> t : trips) {
      if (t.get("lat") != null && km(lat, lng, d(t.get("lat"), 0), d(t.get("lng"), 0)) <= d(t.get("radius_km"), 25)) return true;
      hideHome = hideHome || Boolean.TRUE.equals(t.get("hide_home"));
    }
    if (hideHome) return false;

    int wd = day.getDayOfWeek().getValue();
    for (Map<String, Object> r : jdbc.queryForList(
        "SELECT lat, lng, radius_km FROM professional_area_day_ WHERE professional_id = ? AND weekday = ?", pro.getId(), wd)) {
      if (r.get("lat") != null && km(lat, lng, d(r.get("lat"), 0), d(r.get("lng"), 0)) <= d(r.get("radius_km"), 10)) return true;
    }

    List<Map<String, Object>> area = jdbc.queryForList("SELECT * FROM professional_area_ WHERE professional_id = ?", pro.getId());
    if (area.isEmpty()) return trips.isEmpty() ? null : Boolean.FALSE;
    Map<String, Object> a = area.get(0);
    double cLat = a.get("center_lat") != null ? d(a.get("center_lat"), 0) : (pro.getLatitude() != null ? pro.getLatitude() : 0);
    double cLng = a.get("center_lng") != null ? d(a.get("center_lng"), 0) : (pro.getLongitude() != null ? pro.getLongitude() : 0);
    if ("NEIGHBORHOODS".equals(a.get("mode"))) {
      String list = Objects.toString(a.get("neighborhoods"), "");
      if (neighborhood != null && !neighborhood.isBlank()) {
        String n = plain(neighborhood);
        for (String b : list.split("\n")) if (!b.isBlank() && (plain(b).equals(n) || n.contains(plain(b)) || plain(b).contains(n))) return true;
        return false;
      }
      // sem o nome do bairro do cliente, usa uma distância aproximada da base
      return cLat != 0 && km(lat, lng, cLat, cLng) <= 12;
    }
    return cLat != 0 && km(lat, lng, cLat, cLng) <= d(a.get("radius_km"), 10);
  }

  /** IDs de profissionais em viagem hoje perto deste ponto (para aparecer na busca da cidade de destino). */
  public List<Long> travellersNear(double lat, double lng) {
    List<Long> out = new ArrayList<>();
    for (Map<String, Object> t : jdbc.queryForList(
        "SELECT professional_id, lat, lng, radius_km FROM professional_trip_ WHERE ? BETWEEN start_date AND end_date AND lat IS NOT NULL",
        Date.valueOf(today()))) {
      if (km(lat, lng, d(t.get("lat"), 0), d(t.get("lng"), 0)) <= d(t.get("radius_km"), 25)) out.add(((Number) t.get("professional_id")).longValue());
    }
    return out;
  }

  public Map<String, Object> summary(Long proId) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> area = jdbc.queryForList(
        "SELECT mode, center_label AS \"centerLabel\", radius_km AS \"radiusKm\", neighborhoods, service_modes AS \"serviceModes\", place_address AS \"placeAddress\", place_lat AS \"placeLat\", place_lng AS \"placeLng\" FROM professional_area_ WHERE professional_id = ?", proId);
    out.put("area", area.isEmpty() ? null : area.get(0));
    out.put("days", jdbc.queryForList("SELECT weekday, label, radius_km AS \"radiusKm\" FROM professional_area_day_ WHERE professional_id = ? ORDER BY weekday", proId));
    out.put("trips", jdbc.queryForList(
        "SELECT city, start_date AS \"startDate\", end_date AS \"endDate\" FROM professional_trip_ WHERE professional_id = ? AND end_date >= ? ORDER BY start_date LIMIT 5",
        proId, Date.valueOf(today())));
    return out;
  }

  public static BigDecimal bd(Object o, String def) {
    try { return new BigDecimal(Objects.toString(o, def).replace(',', '.')); } catch (Exception e) { return new BigDecimal(def); }
  }
}
