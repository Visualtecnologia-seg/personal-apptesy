package br.com.personalfighters.schedule;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Libera automaticamente o pagamento em escrow após 48 horas da criação do pedido,
 * desde que não haja disputa aberta.
 *
 * Regras:
 *  - status = DONE
 *  - pixPaid = true  (pagamento foi realizado via PIX)
 *  - pixReleased != true  (ainda não liberado)
 *  - disputeStatus IS NULL ou != 'OPEN'
 *  - createdAt < agora - 48h
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAutoReleaseSchedule {

  private final OrderRepository orderRepository;
  private final AsaasService asaasService;
  private final NotificationService notificationService;

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void autoReleaseExpiredEscrow() {
    LocalDateTime threshold = LocalDateTime.now().minusHours(48);
    List<Order> orders = orderRepository.findDoneOrdersEligibleForAutoRelease(Status.DONE, threshold);

    if (orders.isEmpty()) {
      return;
    }

    log.info("Auto-release: {} pedido(s) elegível(is) para liberação automática de escrow", orders.size());

    for (Order order : orders) {
      try {
        asaasService.releasePaymentToProfessional(order);
        order.setPixReleased(true);
        order.setStatus(Status.REVIEWED);
        orderRepository.save(order);
        notificationService.sendPaymentReleasedToProfessional(order);
        log.info("Escrow liberado automaticamente para pedido {} após 48h", order.getId());
      } catch (Exception e) {
        log.error("Falha na liberação automática do pedido {}: {}", order.getId(), e.getMessage());
      }
    }
  }
}
