package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "finance_")
@JsonView(Views.FinanceView.FirstLevel.class)
public class Finance {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne
  private User user;

  private String customerPaymentProfile;

  private BigDecimal customerBalance;
  private BigDecimal professionalBalance;

}
