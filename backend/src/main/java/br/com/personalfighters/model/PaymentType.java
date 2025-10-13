package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PaymentType {
  CUSTOMER_BALANCE("CUSTOMER_BALANCE"), CREDIT_CARD("CREDIT_CARD"), CUSTOMER_BALANCE_AND_CREDIT_CARD("CUSTOMER_BALANCE_AND_CREDIT_CARD");
  private final String paymentType;
}
