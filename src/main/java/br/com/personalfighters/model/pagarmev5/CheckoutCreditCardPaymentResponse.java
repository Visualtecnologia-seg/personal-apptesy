package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CheckoutCreditCardPaymentResponse {
  private String statementDescriptor;
  private List<CheckoutCardInstallmentOptionsResponse> installments;
  //    private GetPaymentAuthenticationResponse authentication;
}
