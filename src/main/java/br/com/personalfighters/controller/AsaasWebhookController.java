package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/asaas")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class AsaasWebhookController {

  private final TransactionRepository transactionRepository;
  private final OrderRepository orderRepository;
  private final br.com.personalfighters.service.ExtrasService extrasService;
  private final ClassController classController;
  private final GrowthController growthController;
  private final ProGrowthController proGrowthController;
  private final NotificationService notificationService;
  private final AsaasService asaasService;

  @Value("${asaas.webhook.token:}")
  private String webhookToken;

  @PostMapping("/webhook")
  public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload,
                                             HttpServletRequest request) {
    // Sem o token configurado, qualquer um poderia avisar "pago" sem pagar: recusa sempre.
    if (webhookToken == null || webhookToken.isBlank()) {
      log.warn("Asaas webhook recusado: configure ASAAS_WEBHOOK_TOKEN no servidor e no painel do Asaas");
      return ResponseEntity.status(401).build();
    }
    String incoming = request.getHeader("asaas-access-token");
    if (!webhookToken.equals(incoming)) {
      log.warn("Asaas webhook rejeitado: token inválido (IP={})", request.getRemoteAddr());
      return ResponseEntity.status(401).build();
    }
    try {
      String event = (String) payload.get("event");
      Map<String, Object> payment = (Map<String, Object>) payload.get("payment");

      if (payment == null) {
        return ResponseEntity.ok().build();
      }

      String asaasPaymentId = (String) payment.get("id");
      String status = (String) payment.get("status");

      log.info("Asaas webhook: event={}, paymentId={}, status={}", event, asaasPaymentId, status);

      if (asaasPaymentId == null) {
        return ResponseEntity.ok().build();
      }

      Optional<Transaction> txOpt = transactionRepository.findByAsaasPaymentId(asaasPaymentId);
      if (txOpt.isEmpty() && ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event))
          && (extrasService.markPaidByPixId(asaasPaymentId) || classController.markPaidByPixId(asaasPaymentId)
              || growthController.markPackagePaidByPixId(asaasPaymentId) || proGrowthController.markBoostPaidByPixId(asaasPaymentId))) {
        return ResponseEntity.ok().build();
      }
      if (txOpt.isEmpty()) {
        log.warn("Transaction not found for asaasPaymentId={}", asaasPaymentId);
        return ResponseEntity.ok().build();
      }

      Transaction transaction = txOpt.get();
      Order order = transaction.getOrder();

      if ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event)
          || "CONFIRMED".equals(status) || "RECEIVED".equals(status)) {

        transaction.setPaymentStatus(PaymentStatus.PAID);
        transactionRepository.save(transaction);

        order.setPixPaid(true);
        order.setStatus(Status.AWAITING_PAYMENT);
        orderRepository.save(order);

        if (order.getProfessional() != null) {
          notificationService.sendPaymentConfirmedToProfessional(order);
        }
        notificationService.sendPaymentLockedToCustomer(order);

        log.info("PIX confirmed for order {}. Status -> AWAITING_PAYMENT", order.getId());
      }

    } catch (Exception e) {
      log.error("Erro ao processar webhook Asaas: {}", e.getMessage(), e);
    }

    return ResponseEntity.ok().build();
  }
}
