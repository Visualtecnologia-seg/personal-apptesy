package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDTO {

  private Long id;
  private String name;
  private String surname;
  private String avatarUrl;
  private String cpf;
  private String email;
  private Boolean active;

  private String customerPaymentProfile;
  private BigDecimal professionalBalance;

  private String pixCpf;
  private String pixPhoneNumber;
  private String pixEmail;
}
