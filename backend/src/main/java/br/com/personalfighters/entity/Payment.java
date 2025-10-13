package br.com.personalfighters.entity;

import br.com.personalfighters.model.PaymentType;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payment_")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  private PaymentType paymentType;

  @ManyToOne
  private CreditCard card;

  private BigDecimal cardValue;
  private BigDecimal pfCashValue;

}
