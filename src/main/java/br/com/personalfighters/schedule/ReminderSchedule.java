package br.com.personalfighters.schedule;

import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lembrete 1 hora antes do serviço, para o cliente e para o profissional. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderSchedule {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final NotificationService notificationService;
  private final UserService userService;

  @Scheduled(fixedRate = 300_000, initialDelay = 90_000)
  public void remind() {
    LocalDateTime now = LocalDateTime.now(Z);
    List<Map<String, Object>> rows;
    try {
      rows = jdbc.queryForList(
          "SELECT o.id, o.customer_id, o.professional_id, o.start_time, o.service_address, pr.name AS svc FROM order_ o LEFT JOIN product_ pr ON pr.id = o.product_id " +
              "WHERE o.status IN ('CONFIRMED','AWAITING_PAYMENT','IN_PROGRESS') AND o.date IS NOT NULL AND o.start_time IS NOT NULL " +
              "AND (o.date + o.start_time) BETWEEN ? AND ? AND NOT EXISTS (SELECT 1 FROM order_reminder_ r WHERE r.order_id = o.id)",
          Timestamp.valueOf(now.plusMinutes(40)), Timestamp.valueOf(now.plusMinutes(70)));
    } catch (Exception e) { log.debug("lembretes: {}", e.getMessage()); return; }
    for (Map<String, Object> o : rows) {
      UUID id = (UUID) o.get("id");
      try {
        jdbc.update("INSERT INTO order_reminder_ (order_id, sent_at) VALUES (?, ?) ON CONFLICT DO NOTHING", id, Timestamp.valueOf(now));
        String hora = String.valueOf(o.get("start_time")).substring(0, 5);
        String svc = o.get("svc") == null ? "seu serviço" : o.get("svc").toString();
        if (o.get("customer_id") != null)
          notificationService.sendToUser(userService.findById(((Number) o.get("customer_id")).longValue()), false, "Lembrete: " + svc + " hoje às " + hora + ". O profissional já está se organizando.");
        if (o.get("professional_id") != null)
          notificationService.sendToUser(userService.findById(((Number) o.get("professional_id")).longValue()), true, "Lembrete: atendimento às " + hora + " em " + o.get("service_address") + ". Toque para abrir a rota.");
      } catch (Exception e) { log.warn("Lembrete do pedido {} falhou: {}", id, e.getMessage()); }
    }
  }
}
