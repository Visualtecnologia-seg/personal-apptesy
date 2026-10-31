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

  @Override
  public Optional<Transaction> find(Long id) {
    return transactionRepository.findById(id);
  }

  @Override
  public Optional<Transaction> findByOrderId(UUID id) {
    return transactionRepository.findTransactionByOrderId(id);
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
    Optional<Transaction> txOpt = findByOrderId(order.getId());
    if (txOpt.isEmpty()) {
      log.warn("refundPayment: no transaction found for order {}", order.getId());
      return;
    }
    Transaction tx = txOpt.get();
    if (tx.getAsaasPaymentId() != null && !tx.getAsaasPaymentId().isBlank()) {
      log.info("refundPayment: Asaas refund for payment {}", tx.getAsaasPaymentId());
      tx.setPaymentStatus(PaymentStatus.REFUNDED);
      transactionRepository.save(tx);
    } else {
      log.info("refundPayment: no Asaas paymentId, skipping refund for order {}", order.getId());
    }
  }

  private BigDecimal calculateTaxValue(BigDecimal value, BigDecimal taxPercentage) {
    return value.multiply(taxPercentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
  }
}
