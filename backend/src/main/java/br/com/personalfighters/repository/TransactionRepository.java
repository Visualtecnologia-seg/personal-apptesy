package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  Optional<Transaction> findTransactionByOrderId(UUID orderId);
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
