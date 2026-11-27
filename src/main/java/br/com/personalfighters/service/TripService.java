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
  /** Velocidade máxima possível na cidade/estrada para contar km rodados. */
  public static final double MAX_SPEED_KMH = 90.0;
  /** Ponto de saída a mais que isso do cliente não vale para a taxa. */
  public static final double MAX_START_KM = 80.0;
  /** "Saí para o atendimento" só a partir de 3 horas antes do horário marcado. */
  public static final int DEPART_EARLY_MINUTES = 180;
  /** A taxa de deslocamento nunca passa de 30% do valor do serviço. */
  public static final BigDecimal MAX_FEE_SHARE = new BigDecimal("0.30");
  /** O ponto de saída precisa estar até a área de atendimento do profissional + esta folga. */
  public static final double START_MARGIN_KM = 10.0;

  private final JdbcTemplate jdbc;

  public Map<String, Object> trip(UUID orderId) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT o.departed_at, o.depart_lat, o.depart_lng, o.pro_lat, o.pro_lng, o.pro_loc_at, o.service_value, o.total_cost, o.status, COALESCE(o.pro_gps_mocked, FALSE) AS mocked, " +
            "o.date AS o_date, o.start_time AS o_start, (SELECT radius_km FROM professional_area_ pa WHERE pa.professional_id = o.professional_id) AS area_km, " +
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
    // O GPS vem do celular do profissional: só conta o que é fisicamente possível
    // (antes dava para "sair de Brasília" e "estar no Rio" 2 segundos depois e ficar com o valor todo)
    boolean suspicious = Boolean.TRUE.equals(r.get("mocked"));
    if (traveled > 0 && r.get("departed_at") != null) {
      double hours = Math.max(0, (System.currentTimeMillis() - ((java.sql.Timestamp) r.get("departed_at")).getTime()) / 3_600_000.0);
      double possible = hours * MAX_SPEED_KMH + 0.5;
      if (traveled > possible) { suspicious = true; traveled = possible; }
    }
    if (traveled > 0 && dLat != null && cLat != null) {
      double startToClient = km(dLat, dLng, cLat, cLng);
      // saída fora da área de atendimento (+ folga): não vale para a taxa (antes "saía de 30 km" e ficava com quase tudo)
      double maxStart = Math.min(MAX_START_KM, (r.get("area_km") == null ? 15.0 : ((Number) r.get("area_km")).doubleValue()) + START_MARGIN_KM);
      if (startToClient > maxStart) { suspicious = true; traveled = 0; }           // "saiu" longe demais do cliente
      else traveled = Math.min(traveled, startToClient * ROAD_FACTOR + 1);             // não roda mais que o caminho até o cliente
    }
    if (Boolean.TRUE.equals(r.get("mocked"))) traveled = 0;
    if (suspicious) out.put("gpsSuspicious", true);
    out.put("traveledKm", round1(traveled));
    // GPS falso: o cliente não paga deslocamento nenhum
    // saída marcada antes da janela de 3 horas (de antes desta regra) também não cobra
    boolean inWindow = true;
    if (r.get("departed_at") != null && r.get("o_date") != null && r.get("o_start") != null) {
      java.time.LocalDateTime start = java.time.LocalDateTime.of(((java.sql.Date) r.get("o_date")).toLocalDate(), ((java.sql.Time) r.get("o_start")).toLocalTime());
      inWindow = !((java.sql.Timestamp) r.get("departed_at")).toLocalDateTime().isBefore(start.minusMinutes(DEPART_EARLY_MINUTES));
    }
    out.put("cancelFee", fee(r.get("departed_at") != null && inWindow && !Boolean.TRUE.equals(r.get("mocked")), traveled, (BigDecimal) r.get("service_value")));
    return out;
  }

  /** Taxa que o cliente paga se cancelar agora (0 se o profissional ainda não saiu). */
  public BigDecimal fee(boolean departed, double traveledKm, BigDecimal serviceValue) {
    if (!departed) return BigDecimal.ZERO;
    BigDecimal f = PRICE_PER_KM.multiply(BigDecimal.valueOf(traveledKm * 2)).setScale(2, RoundingMode.HALF_UP);
    if (f.compareTo(MIN_FEE) < 0) f = MIN_FEE;
    if (serviceValue != null && serviceValue.signum() > 0) {
      BigDecimal cap = serviceValue.multiply(MAX_FEE_SHARE).setScale(2, RoundingMode.HALF_UP);
      if (f.compareTo(cap) > 0) f = cap.max(MIN_FEE.min(serviceValue));
    }
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
    if (Boolean.TRUE.equals(t.get("gpsSuspicious")) && order.getProfessional() != null) {
      // vai para o alerta do painel: GPS do profissional "pulou" neste pedido
      jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'GPS_SUSPEITO', ?, NOW())",
          order.getProfessional().getId(), "Pedido #" + order.getId().toString().substring(0, 8) + ": localização impossível no deslocamento; taxa de cancelamento limitada");
    }
    return fee;
  }

  public void depart(UUID orderId, double lat, double lng) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    jdbc.update("UPDATE order_ SET departed_at = COALESCE(departed_at, ?), depart_lat = COALESCE(depart_lat, ?), depart_lng = COALESCE(depart_lng, ?), " +
        "pro_lat = ?, pro_lng = ?, pro_loc_at = ? WHERE id = ?", now, lat, lng, lat, lng, now, orderId);
  }

  /**
   * O celular avisou que a posição é de um app de "GPS falso". O pedido fica marcado: a chegada não vale como prova
   * (sem início sozinho), a taxa de deslocamento não é cobrada e o painel recebe um alerta (uma vez por pedido).
   */
  public void markMocked(br.com.personalfighters.entity.Order order) {
    if (jdbc.update("UPDATE order_ SET pro_gps_mocked = TRUE WHERE id = ? AND COALESCE(pro_gps_mocked, FALSE) = FALSE", order.getId()) == 1 && order.getProfessional() != null)
      jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'GPS_FALSO', ?, NOW())",
          order.getProfessional().getId(), "Pedido #" + order.getId().toString().substring(0, 8) + ": o celular informou localização simulada (app de GPS falso)");
  }

  public void markArrived(UUID orderId) {
    jdbc.update("UPDATE order_ SET arrived_at = COALESCE(arrived_at, ?) WHERE id = ?", new Timestamp(System.currentTimeMillis()), orderId);
  }

  public void location(UUID orderId, double lat, double lng) {
    jdbc.update("UPDATE order_ SET pro_lat = ?, pro_lng = ?, pro_loc_at = ? WHERE id = ? AND departed_at IS NOT NULL AND arrived_at IS NULL AND status IN ('CONFIRMED','AWAITING_PAYMENT')",
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
