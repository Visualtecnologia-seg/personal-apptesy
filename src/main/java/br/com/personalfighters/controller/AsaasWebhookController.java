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
  private final br.com.personalfighters.repository.UserRepository userRepository;
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

      // Avisos de transferência (saque): o banco pode recusar DEPOIS que o Asaas aceitou (chave errada, conta encerrada...).
      // Antes isso era ignorado: o dinheiro saía da carteira e não chegava na conta do profissional.
      if (event != null && event.startsWith("TRANSFER_") && payload.get("transfer") instanceof Map) {
        Map<String, Object> t = (Map<String, Object>) payload.get("transfer");
        String ref = java.util.Objects.toString(t.get("externalReference"), "");
        if (ref.startsWith("saque-")) {
          java.util.UUID wid;
          try { wid = java.util.UUID.fromString(ref.substring(6)); } catch (IllegalArgumentException e) { return ResponseEntity.ok().build(); }
          if (event.equals("TRANSFER_FAILED") || event.equals("TRANSFER_CANCELLED")) {
            java.util.List<java.util.Map<String, Object>> w = jdbc.queryForList("SELECT professional_id, amount FROM wallet_entry_ WHERE id = ? AND kind = 'WITHDRAW'", wid);
            if (!w.isEmpty() && jdbc.update("UPDATE wallet_entry_ SET status = 'FAILED' WHERE id = ? AND kind = 'WITHDRAW' AND status IN ('DONE','REVIEW','PENDING')", wid) == 1) {
              Long proId = ((Number) w.get(0).get("professional_id")).longValue();
              jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'SAQUE_DEVOLVIDO', ?, NOW())", proId,
                  "Saque de R$ " + w.get(0).get("amount") + " recusado pelo banco (" + java.util.Objects.toString(t.get("failReason"), event) + "). O valor voltou para a carteira.");
              userRepository.findById(proId).ifPresent(u -> notificationService.sendToUser(u, true,
                  "O banco não aceitou o seu saque de R$ " + w.get(0).get("amount") + ". O dinheiro voltou para a sua carteira: confira a chave PIX ou a conta e tente de novo."));
            }
          } else if (event.equals("TRANSFER_DONE")) {
            jdbc.update("UPDATE wallet_entry_ SET status = 'DONE' WHERE id = ? AND kind = 'WITHDRAW' AND status IN ('REVIEW','PENDING')", wid);
          }
        }
        return ResponseEntity.ok().build();
      }

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
        if ("PAYMENT_RECEIVED".equals(event) || "PAYMENT_CONFIRMED".equals(event)) {
          // Vaga, pacote, adicional ou destaque pago depois de expirar/cancelar: o dinheiro volta (antes ficava parado sem nada em troca)
          Long n = jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM class_enrollment_ WHERE pix_payment_id = ? AND status <> 'PAID') + " +
              "(SELECT COUNT(*) FROM package_purchase_ WHERE pix_payment_id = ? AND status <> 'PAID') + " +
              "(SELECT COUNT(*) FROM order_extra_ WHERE pix_payment_id = ? AND status NOT IN ('PAID','REFUNDED','REFUNDING','REFUND_PENDING')) + " +
              "(SELECT COUNT(*) FROM pro_boost_ WHERE pix_payment_id = ? AND status NOT IN ('ACTIVE','DONE','EXPIRED','PAID'))", Long.class,
              asaasPaymentId, asaasPaymentId, asaasPaymentId, asaasPaymentId);
          if (n != null && n > 0 && jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) SELECT NULL, 'PAGO_FORA_DO_PRAZO', ?, NOW() " +
              "WHERE NOT EXISTS (SELECT 1 FROM security_event_ WHERE kind = 'PAGO_FORA_DO_PRAZO' AND detail = ?)", "PIX " + asaasPaymentId, "PIX " + asaasPaymentId) == 1) {
            String err = asaasService.refundPix(asaasPaymentId, null);
            log.warn("PIX {} pago depois de expirar/cancelar: devolução {}", asaasPaymentId, err == null ? "feita" : "PENDENTE: " + err);
          }
        }
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

        if (transaction.getPaymentStatus() == PaymentStatus.PAID || transaction.getPaymentStatus() == PaymentStatus.REFUNDED
            || transaction.getPaymentStatus() == PaymentStatus.PENDING_REFUND || transaction.getPaymentStatus() == PaymentStatus.CHARGEDBACK) {
          return ResponseEntity.ok().build(); // aviso repetido (inclusive de pagamento já devolvido ou contestado: não devolve de novo)
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
        // A cobrança paga tem que valer o preço ATUAL do pedido (uma cobrança velha e barata paga depois de o preço subir não vale)
        java.math.BigDecimal due = jdbc.queryForObject("SELECT GREATEST(COALESCE(total_cost, 0) - COALESCE(discount_amount, 0), 1) FROM order_ WHERE id = ?", java.math.BigDecimal.class, order.getId());
        boolean stale = transaction.getTotalPaid() != null && due != null && transaction.getTotalPaid().compareTo(due.subtract(new java.math.BigDecimal("0.01"))) < 0;
        if (stale || paidValue != null && transaction.getTotalPaid() != null && paidValue.compareTo(transaction.getTotalPaid().subtract(new java.math.BigDecimal("0.01"))) < 0) {
          jdbc.update("UPDATE order_ SET pix_paid = FALSE, status = ? WHERE id = ?", order.getStatus().name(), order.getId()); // desfaz: não valeu
          jdbc.update("UPDATE transaction_ SET refund_status = 'STARTED' WHERE id = ?", transaction.getId());
          String err = asaasService.refundPix(asaasPaymentId, null);
          transaction.setPaymentStatus(err == null ? PaymentStatus.REFUNDED : PaymentStatus.PENDING_REFUND);
          transactionRepository.save(transaction);
          if (order.getCustomer() != null) notificationService.sendToUser(order.getCustomer(), true, "O valor do pedido mudou depois que o PIX foi gerado. Devolvemos o pagamento: gere o PIX de novo com o valor certo.");
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
