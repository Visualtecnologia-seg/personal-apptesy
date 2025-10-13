package br.com.personalfighters.entity;

import br.com.personalfighters.model.Auditable;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.PaymentStatusReason;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "transaction_")
@JsonView(Views.FinanceView.FirstLevel.class)
public class Transaction extends Auditable<String> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Enumerated(EnumType.STRING)
  private PaymentType paymentType;

  @OneToOne
  private Order order;

  private String paymentTransactionId;

  private String acquirerName;

  @Enumerated(EnumType.STRING)
  private PaymentStatus paymentStatus;

  @Enumerated(EnumType.STRING)
  private PaymentStatusReason refuseReason;

  private BigDecimal customerBalanceValue;

  private BigDecimal creditCardValue;

  private BigDecimal totalPaid;

  @OneToOne
  private CreditCard card;

  @JsonBackReference
  @ManyToOne
  private User customer;

  @JsonBackReference
  @ManyToOne
  private User professional;

  private BigDecimal processingCost;

  private BigDecimal paymentMdrTax;

  private BigDecimal paymentMdrValue;

  private BigDecimal paymentTotalCost;

  private BigDecimal appProfit;

  private BigDecimal appTaxPercentage;

  private BigDecimal appTaxValue;

  private BigDecimal professionalValue;

  private LocalDate billingOrderDate;

  private Boolean orderIsDone;

  private Boolean professionalIsPaid;

  private LocalDate professionalPaymentDate;

  @PostPersist
  void defaultValues() {
    this.paymentMdrTax = BigDecimal.ZERO;
    this.paymentMdrValue = BigDecimal.ZERO;
    this.appTaxPercentage = new BigDecimal("25");
    this.appTaxValue = BigDecimal.ZERO;
    this.professionalValue = BigDecimal.ZERO;
    this.appProfit = BigDecimal.ZERO;
    this.orderIsDone = false;
    this.professionalIsPaid = false;
  }
}
