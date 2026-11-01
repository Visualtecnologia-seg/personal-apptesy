package br.com.personalfighters.schedule;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras de tempo do cofre:
 * - problema relatado fica travado no máximo N horas (padrão 2h); sem decisão da equipe, aplica a regra do painel;
 * - 30 minutos antes da liberação automática, lembra o cliente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeTimeoutSchedule {

  private final OrderRepository orderRepository;
  private final CompletionService completionService;
  private final NotificationService notificationService;
  private final TransactionService transactionService;
  private final ExtrasService extrasService;
  private final SettingsService settings;
  private final JdbcTemplate jdbc;

  @Scheduled(fixedRate = 300_000, initialDelay = 90_000)
  public void tick() {
    try { timeouts(); } catch (Exception e) { log.warn("Prazo de disputas: {}", e.getMessage()); }
    try { reminders(); } catch (Exception e) { log.warn("Lembretes de liberação: {}", e.getMessage()); }
  }

  private void timeouts() {
    int hours = Math.max(1, settings.getInt(SettingsService.DISPUTE_TIMEOUT_HOURS));
    boolean refund = settings.getInt(SettingsService.DISPUTE_TIMEOUT_REFUND) != 0;
    LocalDateTime cutoff = completionService.now().minusHours(hours);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id FROM order_ WHERE dispute_status = 'OPEN' AND disputed_at IS NOT NULL AND disputed_at < ?", Timestamp.valueOf(cutoff));
    for (Map<String, Object> r : rows) {
      UUID id = (UUID) r.get("id");
      Order o = orderRepository.findById(id).orElse(null);
      if (o == null) continue;
      String note = "Sem decisão da equipe em " + hours + "h (regra automática)";
      if (refund) {
        o.setDisputeStatus("RESOLVED_CLIENT");
        o.setStatus(Status.CANCELLED);
        orderRepository.save(o);
        BigDecimal paid = Boolean.TRUE.equals(o.getPixPaid()) && o.getTotalCost() != null
            ? o.getTotalCost().subtract(o.getDiscountAmount() == null ? BigDecimal.ZERO : o.getDiscountAmount()).add(extrasService.paidTotal(id))
            : BigDecimal.ZERO;
        jdbc.update("UPDATE order_ SET dispute_resolution = ?, client_refund = ? WHERE id = ?", "Sistema · " + note, paid, id);
        try { transactionService.refundPayment(o); } catch (Exception e) { log.warn("Reembolso automático não registrado: {}", e.getMessage()); }
        notificationService.sendToUser(o.getCustomer(), false, "O problema que você relatou não foi analisado a tempo, então o valor pago será devolvido a você.");
        if (o.getProfessional() != null) notificationService.sendToUser(o.getProfessional(), true,
            "O problema relatado pelo cliente não foi analisado em " + hours + "h e o valor voltou para ele. Fale com o suporte se discordar.");
      } else {
        o.setDisputeStatus("RESOLVED_PRO");
        orderRepository.save(o);
        jdbc.update("UPDATE order_ SET dispute_resolution = ? WHERE id = ?", "Sistema · " + note, id);
        completionService.finalizeAndRelease(o, "disputa sem decisão no prazo");
        notificationService.sendToUser(o.getCustomer(), false, "O problema relatado não foi analisado a tempo e o pagamento foi liberado ao profissional. Fale com o suporte se precisar.");
      }
      log.info("Disputa do pedido {} encerrada automaticamente ({})", id, refund ? "devolvido" : "liberado");
    }
  }

  private void reminders() {
    LocalDateTime now = completionService.now();
    LocalDateTime from = now.minusHours(CompletionService.AUTO_RELEASE_HOURS);
    LocalDateTime to = now.minusHours(CompletionService.AUTO_RELEASE_HOURS).plusMinutes(30);
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id FROM order_ WHERE status = ? AND COALESCE(release_reminded, false) = false AND COALESCE(dispute_status, '') <> 'OPEN' " +
            "AND requested_completion_at > ? AND requested_completion_at <= ?",
        Status.PENDING_CLIENT_CONFIRMATION.name(), Timestamp.valueOf(from), Timestamp.valueOf(to));
    for (Map<String, Object> r : rows) {
      UUID id = (UUID) r.get("id");
      Order o = orderRepository.findById(id).orElse(null);
      if (o == null) continue;
      jdbc.update("UPDATE order_ SET release_reminded = true WHERE id = ?", id);
      notificationService.sendToUser(o.getCustomer(), false, "Faltam 30 minutos para o pagamento ser liberado sozinho. Se tiver algum problema, avise agora.");
    }
  }
}
