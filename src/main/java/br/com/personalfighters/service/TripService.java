package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deslocamento do profissional ("Saí para o atendimento"), acompanhamento ao vivo
 * e taxa de cancelamento por km: (km rodados + volta) × R$ 1,50, mínimo R$ 10, no máximo o valor do serviço.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

  public static volatile BigDecimal PRICE_PER_KM = new BigDecimal("1.50");
  public static volatile BigDecimal MIN_FEE = new BigDecimal("10.00");
  /** Linha reta × 1,3 ≈ distância pelas ruas. */
  public static final double ROAD_FACTOR = 1.3;
  public static final double AVG_SPEED_KMH = 30.0;

  private final JdbcTemplate jdbc;

  public Map<String, Object> trip(UUID orderId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT o.departed_at, o.depart_lat, o.depart_lng, o.pro_lat, o.pro_lng, o.pro_loc_at, o.service_value, o.total_cost, o.status, " +
            "a.latitude AS a_lat, a.longitude AS a_lng, u.latitude AS u_lat, u.longitude AS u_lng " +
            "FROM order_ o LEFT JOIN address_ a ON a.id = o.address_id LEFT JOIN user_ u ON u.id = o.customer_id WHERE o.id = ?", orderId);
    Map<String, Object> out = new LinkedHashMap<>();
    if (rows.isEmpty()) return out;
    Map<String, Object> r = rows.get(0);
    Double cLat = num(r.get("a_lat")), cLng = num(r.get("a_lng"));
    if (cLat == null || cLng == null) { cLat = num(r.get("u_lat")); cLng = num(r.get("u_lng")); }
    Double dLat = num(r.get("depart_lat")), dLng = num(r.get("depart_lng"));
    Double pLat = num(r.get("pro_lat")), pLng = num(r.get("pro_lng"));
    if (pLat == null && dLat != null) { pLat = dLat; pLng = dLng; }

    out.put("departed", r.get("departed_at") != null);
    out.put("departedAt", r.get("departed_at"));
    out.put("proLat", pLat);
    out.put("proLng", pLng);
    out.put("proUpdatedAt", r.get("pro_loc_at"));
    out.put("clientLat", cLat);
    out.put("clientLng", cLng);
    if (pLat != null && cLat != null) {
      double km = km(pLat, pLng, cLat, cLng);
      out.put("distanceKm", round1(km));
      out.put("etaMin", (int) Math.max(1, Math.round(km / AVG_SPEED_KMH * 60)));
      out.put("near", km <= 0.3);
    }
    double traveled = 0;
    if (dLat != null && pLat != null) traveled = km(dLat, dLng, pLat, pLng);
    out.put("traveledKm", round1(traveled));
    out.put("cancelFee", fee(r.get("departed_at") != null, traveled, (BigDecimal) r.get("service_value")));
    return out;
  }

  /** Taxa que o cliente paga se cancelar agora (0 se o profissional ainda não saiu). */
  public BigDecimal fee(boolean departed, double traveledKm, BigDecimal serviceValue) {
    if (!departed) return BigDecimal.ZERO;
    BigDecimal f = PRICE_PER_KM.multiply(BigDecimal.valueOf(traveledKm * 2)).setScale(2, RoundingMode.HALF_UP);
    if (f.compareTo(MIN_FEE) < 0) f = MIN_FEE;
    if (serviceValue != null && serviceValue.signum() > 0 && f.compareTo(serviceValue) > 0) f = serviceValue;
    return f;
  }

  /** Chamado no cancelamento pelo cliente: registra a taxa e o reembolso. Retorna a taxa. */
  public BigDecimal applyCancelFee(Order order) {
    Map<String, Object> t = trip(order.getId());
    BigDecimal fee = (BigDecimal) t.getOrDefault("cancelFee", BigDecimal.ZERO);
    if (fee == null) fee = BigDecimal.ZERO;
    BigDecimal paid = Boolean.TRUE.equals(order.getPixPaid()) && order.getTotalCost() != null
        ? order.getTotalCost().subtract(order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount())
        : BigDecimal.ZERO;
    BigDecimal refund = paid.subtract(fee).max(BigDecimal.ZERO);
    jdbc.update("UPDATE order_ SET cancel_fee = ?, client_refund = ? WHERE id = ?", fee, refund, order.getId());
    return fee;
  }

  public void depart(UUID orderId, double lat, double lng) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    jdbc.update("UPDATE order_ SET departed_at = COALESCE(departed_at, ?), depart_lat = COALESCE(depart_lat, ?), depart_lng = COALESCE(depart_lng, ?), " +
        "pro_lat = ?, pro_lng = ?, pro_loc_at = ? WHERE id = ?", now, lat, lng, lat, lng, now, orderId);
  }

  public void markArrived(UUID orderId) {
    jdbc.update("UPDATE order_ SET arrived_at = COALESCE(arrived_at, ?) WHERE id = ?", new Timestamp(System.currentTimeMillis()), orderId);
  }

  public void location(UUID orderId, double lat, double lng) {
    jdbc.update("UPDATE order_ SET pro_lat = ?, pro_lng = ?, pro_loc_at = ? WHERE id = ? AND departed_at IS NOT NULL",
        lat, lng, new Timestamp(System.currentTimeMillis()), orderId);
  }

  public static double km(double lat1, double lng1, double lat2, double lng2) {
    double r = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * ROAD_FACTOR;
  }

  private static double round1(double v) { return Math.round(v * 10) / 10.0; }

  private static Double num(Object o) {
    if (o == null) return null;
    if (o instanceof Number) return ((Number) o).doubleValue();
    try {
      String s = o.toString().trim().replace(',', '.');
      return s.isEmpty() ? null : Double.parseDouble(s);
    } catch (Exception e) {
      return null;
    }
  }
}
