package br.com.personalfighters.service;

import br.com.personalfighters.controller.EngagementController;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regras do OPUS que o Allan muda pelo painel (taxa, km, pontos) e histórico das ações da equipe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService implements ApplicationRunner {

  public static final String FEE_PERCENT = "fee_percent";
  public static final String KM_PRICE = "cancel_km_price";
  public static final String KM_MIN = "cancel_min_fee";
  public static final String POINTS_PER_SERVICE = "points_per_service";
  public static final String POINTS_MIN_SERVICE = "points_min_service";
  public static final String REDEEM_MIN_ORDER = "redeem_min_order";
  public static final String REFERRAL_POINTS = "referral_points";
  /** Cupom de boas-vindas (quem não fez o 1º pedido) e de aniversário, em % do pedido. 0 = não manda cupom. */
  public static final String WELCOME_COUPON_PERCENT = "welcome_coupon_percent";
  public static final String BIRTHDAY_COUPON_PERCENT = "birthday_coupon_percent";
  public static final String PACKAGE_MAX_DISCOUNT = "package_max_discount";
  /** Horas que um problema relatado pode ficar travado sem decisão. */
  public static final String DISPUTE_TIMEOUT_HOURS = "dispute_timeout_hours";
  /** 1 = sem decisão no prazo, devolve ao cliente; 0 = libera ao profissional. */
  public static final String DISPUTE_TIMEOUT_REFUND = "dispute_timeout_refund";

  private final JdbcTemplate jdbc;

  @Override
  public void run(ApplicationArguments args) {
    try { apply(); } catch (Exception e) { log.warn("Configurações não carregadas: {}", e.getMessage()); }
  }

  public Map<String, String> all() {
    Map<String, String> m = defaults();
    for (Map<String, Object> r : jdbc.queryForList("SELECT key, value FROM platform_setting_")) {
      m.put((String) r.get("key"), (String) r.get("value"));
    }
    return m;
  }

  public String get(String key) {
    return all().get(key);
  }

  public int getInt(String key) {
    try { return Integer.parseInt(get(key)); } catch (Exception e) { return Integer.parseInt(defaults().get(key)); }
  }

  public void set(String key, String value, User admin) {
    if (!defaults().containsKey(key)) throw new IllegalArgumentException("Configuração desconhecida: " + key);
    BigDecimal n;
    try { n = new BigDecimal(value.trim().replace(',', '.')); } catch (Exception e) { throw new IllegalArgumentException("Valor inválido para " + key); }
    if (n.signum() < 0) throw new IllegalArgumentException("O valor não pode ser negativo.");
    if (FEE_PERCENT.equals(key) && n.compareTo(new BigDecimal("30")) > 0) throw new IllegalArgumentException("A taxa máxima é 30%.");
    String v = n.stripTrailingZeros().toPlainString();
    jdbc.update("INSERT INTO platform_setting_ (key, value, updated_at, updated_by) VALUES (?, ?, ?, ?) " +
            "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by",
        key, v, Timestamp.valueOf(LocalDateTime.now()), admin != null ? admin.getName() : null);
    audit(admin, "SETTING", key, v);
    apply();
  }

  /** Aplica as regras nas partes do sistema que as usam. */
  public void apply() {
    Map<String, String> m = all();
    Pricing.FEE_RATE = new BigDecimal(m.get(FEE_PERCENT)).divide(new BigDecimal("100"));
    TripService.PRICE_PER_KM = new BigDecimal(m.get(KM_PRICE)).setScale(2, java.math.RoundingMode.HALF_UP);
    TripService.MIN_FEE = new BigDecimal(m.get(KM_MIN)).setScale(2, java.math.RoundingMode.HALF_UP);
    PointsAwarder.POINTS_PER_SERVICE = Integer.parseInt(m.get(POINTS_PER_SERVICE));
    PointsAwarder.MIN_SERVICE_FOR_POINTS = new BigDecimal(m.get(POINTS_MIN_SERVICE));
    EngagementController.MIN_ORDER_FOR_REDEEM = new BigDecimal(m.get(REDEEM_MIN_ORDER));
  }

  public void audit(User admin, String action, String target, String detail) {
    try {
      jdbc.update("INSERT INTO admin_action_ (admin_id, admin_name, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
          admin != null ? admin.getId() : null, admin != null ? admin.getName() : "Sistema", action, target,
          detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail, Timestamp.valueOf(LocalDateTime.now()));
    } catch (Exception e) {
      log.warn("Ação do painel não registrada: {}", e.getMessage());
    }
  }

  private static Map<String, String> defaults() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(FEE_PERCENT, "5");
    m.put(KM_PRICE, "1.5");
    m.put(KM_MIN, "10");
    m.put(POINTS_PER_SERVICE, "10");
    m.put(POINTS_MIN_SERVICE, "20");
    m.put(REDEEM_MIN_ORDER, "100");
    m.put(REFERRAL_POINTS, "100");
    m.put(WELCOME_COUPON_PERCENT, "10");
    m.put(BIRTHDAY_COUPON_PERCENT, "15");
    m.put(PACKAGE_MAX_DISCOUNT, "30");
    m.put(DISPUTE_TIMEOUT_HOURS, "2");
    m.put(DISPUTE_TIMEOUT_REFUND, "1");
    return m;
  }
}
