package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.service.AsaasService;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.service.TransactionService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

  private static final String APP_TAX_PERCENTAGE = "5";

  private final TransactionRepository transactionRepository;
  private final FinanceService financeService;
  private final AsaasService asaasService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Override
  public Optional<Transaction> find(Long id) {
    return transactionRepository.findById(id);
  }

  @Override
  public Optional<Transaction> findByOrderId(UUID id) {
    // Pode haver mais de uma cobrança no mesmo pedido: vale a paga; se nenhuma, a mais recente
    java.util.List<Transaction> all = transactionRepository.findAllByOrderId(id);
    return all.stream().filter(t -> t.getPaymentStatus() == PaymentStatus.PAID).findFirst()
        .or(() -> all.stream().max(java.util.Comparator.comparing(Transaction::getId)));
  }

  @Override
  public Transaction save(Transaction transaction) {
    return transactionRepository.save(transaction);
  }

  @Override
  public Transaction doHoldPayment(Order order) {
    log.info("doHoldPayment: PIX flow — charge created separately via /orders/{id}/pix");
    return Transaction.builder()
        .order(order)
        .paymentStatus(PaymentStatus.AUTHORIZED)
        .totalPaid(BigDecimal.ZERO)
        .creditCardValue(BigDecimal.ZERO)
        .customerBalanceValue(BigDecimal.ZERO)
        .appTaxPercentage(new BigDecimal(APP_TAX_PERCENTAGE))
        .appTaxValue(BigDecimal.ZERO)
        .professionalValue(BigDecimal.ZERO)
        .processingCost(BigDecimal.ZERO)
        .paymentMdrTax(BigDecimal.ZERO)
        .paymentMdrValue(BigDecimal.ZERO)
        .paymentTotalCost(BigDecimal.ZERO)
        .appProfit(BigDecimal.ZERO)
        .orderIsDone(false)
        .professionalIsPaid(false)
        .build();
  }

  @Override
  public Transaction doPayment(Order order) {
    return findByOrderId(order.getId())
        .map(t -> {
          t.setPaymentStatus(PaymentStatus.PAID);
          return transactionRepository.save(t);
        })
        .orElse(Transaction.builder()
            .order(order)
            .paymentStatus(PaymentStatus.PAID)
            .totalPaid(order.getTotalCost())
            .appTaxPercentage(new BigDecimal(APP_TAX_PERCENTAGE))
            .appTaxValue(calculateTaxValue(order.getTotalCost(), new BigDecimal(APP_TAX_PERCENTAGE)))
            .professionalValue(order.getTotalCost().multiply(new BigDecimal("0.95")))
            .build());
  }

  @Override
  public Transaction update(Transaction transaction) {
    find(transaction.getId()).orElseThrow(() -> new ObjectNotFoundException("Transação não encontrada."));
    return transactionRepository.save(transaction);
  }

  @Override
  public Transaction giveCreditToCustomer(Order order) {
    Finance userFinance = financeService.findByUser(order.getCustomer().getId());
    userFinance.setCustomerBalance(userFinance.getCustomerBalance().add(order.getTotalCost()));
    financeService.save(userFinance);
    Transaction transaction = findByOrderId(order.getId())
        .orElseThrow(() -> new ObjectNotFoundException("Transaction not found."));
    transaction.setPaymentStatus(PaymentStatus.CUSTOMER_BALANCE);
    return update(transaction);
  }

  @Override
  public void refundPayment(Order order) {
    refundPayment(order, BigDecimal.ZERO);
  }

  @Override
  public void refundPayment(Order order, BigDecimal keep) {
    BigDecimal hold = keep == null ? BigDecimal.ZERO : keep.max(BigDecimal.ZERO);
    for (Transaction tx : transactionRepository.findAllByOrderId(order.getId())) {
      String pid = tx.getAsaasPaymentId();
      if (tx.getPaymentStatus() == PaymentStatus.PAID) {
        if (pid != null && pid.startsWith("PKG-")) {
          // Pago com aula de pacote: devolve a aula ao pacote
          jdbc.update("UPDATE package_purchase_ SET sessions_used = GREATEST(sessions_used - 1, 0) WHERE id = (SELECT package_purchase_id FROM order_ WHERE id = ?)", order.getId());
          tx.setPaymentStatus(PaymentStatus.REFUNDED);
          transactionRepository.save(tx);
          continue;
        }
        BigDecimal paid = tx.getTotalPaid() == null ? BigDecimal.ZERO : tx.getTotalPaid();
        BigDecimal back = paid.subtract(hold).max(BigDecimal.ZERO);
        hold = hold.subtract(paid).max(BigDecimal.ZERO);
        String err = back.signum() > 0 ? asaasService.refundPix(pid, back) : null;
        if (err == null) {
          tx.setPaymentStatus(PaymentStatus.REFUNDED);
          transactionRepository.save(tx);
          jdbc.update("UPDATE transaction_ SET refund_status = 'DONE', refunded_at = NOW(), refund_error = NULL WHERE id = ?", tx.getId());
          log.info("Estorno de R$ {} feito para o pedido {}", back, order.getId());
        } else {
          // Não conseguiu devolver: fica pendente para o administrador resolver no painel
          tx.setPaymentStatus(PaymentStatus.PENDING_REFUND);
          transactionRepository.save(tx);
          jdbc.update("UPDATE transaction_ SET refund_status = 'FAILED', refund_error = ? WHERE id = ?", err, tx.getId());
          log.error("ESTORNO PENDENTE pedido {} cobrança {}: {}", order.getId(), pid, err);
        }
      } else if (tx.getPaymentStatus() == PaymentStatus.AUTHORIZED || tx.getPaymentStatus() == PaymentStatus.WAITING_PAYMENT) {
        // Cobrança ainda não paga: cancela no Asaas para ninguém pagar depois
        asaasService.cancelCharge(pid);
        tx.setPaymentStatus(PaymentStatus.REFUSED);
        transactionRepository.save(tx);
      }
    }
  }

  private BigDecimal calculateTaxValue(BigDecimal value, BigDecimal taxPercentage) {
    return value.multiply(taxPercentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
  }
}
