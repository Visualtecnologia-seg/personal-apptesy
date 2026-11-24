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
  private final br.com.personalfighters.service.WalletService walletService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private static final Object LOCK = new Object();

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
    if (incoming == null || !java.security.MessageDigest.isEqual(webhookToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), incoming.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
      log.warn("Asaas webhook rejeitado: token inválido (IP={})", request.getRemoteAddr());
      return ResponseEntity.status(401).build();
    }
    synchronized (LOCK) { return handle(payload); } // avisos do mesmo pagamento chegando juntos são tratados um de cada vez
  }

  private ResponseEntity<Void> handle(Map<String, Object> payload) {
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

      // Dinheiro devolvido ao cliente pelo banco/Asaas sem ser o OPUS que pediu (MED do PIX, contestação):
      // o pagamento do profissional não sai, ou é descontado da carteira se já tinha saído.
      if (event != null && (event.equals("PAYMENT_REFUNDED") || event.equals("PAYMENT_PARTIALLY_REFUNDED") || event.equals("PAYMENT_REFUND_IN_PROGRESS")
          || event.startsWith("PAYMENT_CHARGEBACK"))) {
        String rs = jdbc.queryForList("SELECT COALESCE(refund_status, '') FROM transaction_ WHERE id = ?", String.class, transaction.getId()).stream().findFirst().orElse("");
        if (transaction.getPaymentStatus() == PaymentStatus.PAID && rs.isEmpty()) {
          jdbc.update("UPDATE transaction_ SET payment_status = 'CHARGEDBACK', refund_status = 'EXTERNAL', refunded_at = NOW() WHERE id = ?", transaction.getId());
          if (Boolean.TRUE.equals(order.getPixReleased())) {
            walletService.reverseOrder(order, "pagamento devolvido pelo banco do cliente");
          } else {
            jdbc.update("UPDATE order_ SET dispute_status = 'OPEN', dispute_reason = ?, disputed_at = NOW() WHERE id = ?",
                "Pagamento devolvido pelo banco do cliente (" + event + "). Conferir antes de liberar.", order.getId());
          }
          Long who = order.getProfessional() == null ? (order.getCustomer() == null ? null : order.getCustomer().getId()) : order.getProfessional().getId();
          jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'PAGAMENTO_DEVOLVIDO', ?, NOW())", who,
              "Pedido #" + order.getId().toString().substring(0, 8) + ": o banco devolveu o pagamento ao cliente (" + event + ")");
          log.warn("Pagamento {} do pedido {} devolvido fora do OPUS ({})", asaasPaymentId, order.getId(), event);
        }
        return ResponseEntity.ok().build();
      }

      if ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event)
          || "CONFIRMED".equals(status) || "RECEIVED".equals(status)) {

        if (transaction.getPaymentStatus() == PaymentStatus.PAID || transaction.getPaymentStatus() == PaymentStatus.REFUNDED) {
          return ResponseEntity.ok().build(); // aviso repetido
        }
        // Marca o pedido como pago de uma vez só no banco: se duas cobranças chegarem juntas, a segunda é devolvida
        boolean first = jdbc.update("UPDATE order_ SET pix_paid = TRUE, status = 'AWAITING_PAYMENT' WHERE id = ? AND COALESCE(pix_paid, FALSE) = FALSE AND status IN ('OPEN', 'CONFIRMED')", order.getId()) == 1;
        if (!first) {
          // Pago depois do cancelamento (ou pagou duas cobranças): devolve na hora e NÃO reabre o pedido
          jdbc.update("UPDATE transaction_ SET refund_status = 'STARTED' WHERE id = ?", transaction.getId());
          String err = asaasService.refundPix(asaasPaymentId, null);
          transaction.setPaymentStatus(err == null ? PaymentStatus.REFUNDED : PaymentStatus.PENDING_REFUND);
          transactionRepository.save(transaction);
          log.warn("Pagamento {} chegou com o pedido {} em {} (já pago={}): estorno {}", asaasPaymentId, order.getId(), order.getStatus(),
              order.getPixPaid(), err == null ? "feito" : "PENDENTE: " + err);
          return ResponseEntity.ok().build();
        }
        // Confere o valor pago com o valor da cobrança (pagamento a menor não libera o pedido)
        java.math.BigDecimal paidValue = null;
        try { paidValue = new java.math.BigDecimal(String.valueOf(payment.get("value"))); } catch (Exception ignored) { }
        if (paidValue != null && transaction.getTotalPaid() != null && paidValue.compareTo(transaction.getTotalPaid().subtract(new java.math.BigDecimal("0.01"))) < 0) {
          jdbc.update("UPDATE order_ SET pix_paid = FALSE, status = ? WHERE id = ?", order.getStatus().name(), order.getId()); // desfaz: não valeu
          jdbc.update("UPDATE transaction_ SET refund_status = 'STARTED' WHERE id = ?", transaction.getId());
          String err = asaasService.refundPix(asaasPaymentId, null);
          transaction.setPaymentStatus(err == null ? PaymentStatus.REFUNDED : PaymentStatus.PENDING_REFUND);
          transactionRepository.save(transaction);
          log.warn("Pagamento {} do pedido {} veio menor (R$ {} de R$ {}): estornado {}", asaasPaymentId, order.getId(), paidValue, transaction.getTotalPaid(), err == null ? "" : "PENDENTE: " + err);
          return ResponseEntity.ok().build();
        }
        transaction.setPaymentStatus(PaymentStatus.PAID);
        transactionRepository.save(transaction);

        order.setPixPaid(true);
        order.setStatus(Status.AWAITING_PAYMENT);

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
