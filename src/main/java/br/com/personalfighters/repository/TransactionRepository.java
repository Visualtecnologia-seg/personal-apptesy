package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  /** Pode haver mais de uma cobrança no mesmo pedido: vale a paga; se nenhuma, a mais recente. */
  default Optional<Transaction> findTransactionByOrderId(UUID orderId) {
    java.util.List<Transaction> all = findAllByOrderId(orderId);
    return all.stream().filter(t -> t.getPaymentStatus() == br.com.personalfighters.model.PaymentStatus.PAID).findFirst()
        .or(() -> all.stream().max(java.util.Comparator.comparing(Transaction::getId)));
  }

  java.util.List<Transaction> findAllByOrderId(UUID orderId);

  Optional<Transaction> findByAsaasPaymentId(String asaasPaymentId);
//
//  @Query("select sum(t.professionalValue) from Transaction t where t.professional = :professional " +
//      "and (t.billingOrderDate between :firstDateOfWeek and :lastDateOfWeek) " +
//      "and t.orderIsDone = true")
//
//  BigDecimal sumProfessionalValueByDateAndFinance(User professional, LocalDate firstDateOfWeek, LocalDate lastDateOfWeek);
//
//  Iterable<Transaction> findTransactionsByBillingOrderDateAndOrderIsDoneAndProfessionalFinanceId(
//      LocalDate date,
//      Boolean isDone,
//      Long id
//  );
}
