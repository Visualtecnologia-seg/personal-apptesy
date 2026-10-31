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
        asaasService.releasePaymentToProfessional(order);
      } else {
        log.info("Pedido {} sem PIX pago (modo de teste): liberação apenas registrada", order.getId());
      }
      order.setPixReleased(true);
      order.setPaymentReleasedAt(now());
      orderRepository.save(order);
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
        if (o.getRequestedCompletionAt() != null && o.getRequestedCompletionAt().isBefore(cutoff) && !disputeOpen(o)) {
          finalizeAndRelease(o, "automático após " + AUTO_RELEASE_HOURS + "h");
        }
      } catch (Exception e) {
        log.error("Falha na liberação automática do pedido {}: {}", o.getId(), e.getMessage());
      }
    }
  }
}
