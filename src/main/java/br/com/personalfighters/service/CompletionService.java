package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Conclusão do serviço e liberação do pagamento (cofre OPUS):
 *  - o profissional toca "Terminei" → recebe um código de 6 números (também em QR);
 *  - o cliente confere e digita/lê o código → serviço concluído e pagamento liberado na hora;
 *  - se o cliente não aprovar nem reclamar em 2 horas, libera sozinho;
 *  - "Tive um problema" abre disputa e congela o dinheiro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompletionService {

  public static final int AUTO_RELEASE_HOURS = 2;
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private static final SecureRandom RND = new SecureRandom();

  private final OrderRepository orderRepository;
  private final AsaasService asaasService;
  private final NotificationService notificationService;
  private final PointsAwarder pointsAwarder;
  private final ExtrasService extrasService;
  private final WalletService walletService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }

  public String newCode(Order order) {
    String code = String.format("%06d", RND.nextInt(1_000_000));
    order.setFinishCode(code);
    order.setFinishCodeExpiresAt(now().plusHours(AUTO_RELEASE_HOURS + 1));
    return code;
  }

  public boolean disputeOpen(Order order) {
    return "OPEN".equalsIgnoreCase(order.getDisputeStatus());
  }

  /** Marca como concluído, dá os pontos e libera o pagamento ao profissional (se não houver disputa). */
  public void finalizeAndRelease(Order order, String why) {
    // Pedido cancelado (dinheiro devolvido ao cliente) nunca é concluído nem liberado
    if (order.getStatus() == Status.CANCELLED || "CANCELLED".equals(jdbc.queryForObject("SELECT status FROM order_ WHERE id = ?", String.class, order.getId()))) {
      log.warn("Liberação do pedido {} ignorada: pedido cancelado ({})", order.getId(), why);
      return;
    }
    if (order.getStatus() != Status.DONE && order.getStatus() != Status.REVIEWED) {
      order.setStatus(Status.DONE);
    }
    if (order.getDoneAt() == null) order.setDoneAt(now());
    order.setFinishCode(null);
    order.setFinishCodeExpiresAt(null);
    orderRepository.save(order);
    pointsAwarder.award(order);

    if (disputeOpen(order) || Boolean.TRUE.equals(order.getPixReleased())) return;
    try {
      if (Boolean.TRUE.equals(order.getPixPaid())) {
        // O valor sai do cofre e entra na carteira do profissional; ele saca no PIX quando quiser.
        walletService.credit(order);
      } else {
        log.info("Pedido {} sem PIX pago (modo de teste): liberação apenas registrada", order.getId());
      }
      order.setPixReleased(true);
      order.setPaymentReleasedAt(now());
      orderRepository.save(order);
      try { extrasService.release(order); } catch (Exception ex) { log.warn("Extras do pedido {} não liberados: {}", order.getId(), ex.getMessage()); }
      notificationService.sendPaymentReleasedToProfessional(order);
      log.info("Pagamento do pedido {} liberado ({})", order.getId(), why);
    } catch (Exception e) {
      log.error("Falha ao liberar pagamento do pedido {}: {}", order.getId(), e.getMessage());
    }
  }

  /** Plano B: 2 horas depois do "Terminei", sem aprovação nem reclamação, libera sozinho. */
  @Scheduled(fixedRate = 600_000, initialDelay = 120_000)
  public void autoRelease() {
    LocalDateTime cutoff = now().minusHours(AUTO_RELEASE_HOURS);
    List<Order> pending = orderRepository.findByStatus(Status.PENDING_CLIENT_CONFIRMATION);
    for (Order o : pending) {
      try {
        if (o.getRequestedCompletionAt() != null && o.getRequestedCompletionAt().isBefore(cutoff) && !disputeOpen(o) && !sosActive(o) && autoReleaseDue(o)) {
          finalizeAndRelease(o, "automático após " + AUTO_RELEASE_HOURS + "h");
        }
      } catch (Exception e) {
        log.error("Falha na liberação automática do pedido {}: {}", o.getId(), e.getMessage());
      }
    }
  }

  /**
   * Liberação sozinha só depois do fim MARCADO do serviço + 2h (antes, "Terminei" logo depois de chegar liberava 2h depois),
   * e 24h quando o cliente nem confirmou a chegada (o profissional começou sozinho).
   */
  boolean autoReleaseDue(Order o) {
    LocalDateTime now = now();
    if (o.getDate() != null) {
      java.time.LocalTime start = o.getStartTime() == null ? java.time.LocalTime.MIDNIGHT : o.getStartTime();
      LocalDateTime end = o.getEndTime() != null ? o.getDate().atTime(o.getEndTime()) : o.getDate().atTime(start).plusHours(o.getHours() == null ? 1 : o.getHours());
      if (now.isBefore(end.plusHours(AUTO_RELEASE_HOURS))) return false;
    }
    Boolean solo = jdbc.queryForObject("SELECT COALESCE(solo_started, FALSE) FROM order_ WHERE id = ?", Boolean.class, o.getId());
    return !Boolean.TRUE.equals(solo) || o.getRequestedCompletionAt().isBefore(now.minusHours(24));
  }

  /** Enquanto houver SOS ativo ligado ao pedido, a liberação automática fica pausada. */
  private boolean sosActive(Order o) {
    try {
      Long n = jdbc.queryForObject("SELECT COUNT(*) FROM protection_session_ WHERE order_id = ? AND active = TRUE AND level >= 3", Long.class, o.getId());
      return n != null && n > 0;
    } catch (Exception e) { return false; }
  }
}
