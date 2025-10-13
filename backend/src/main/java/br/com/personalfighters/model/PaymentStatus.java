package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PaymentStatus {
  PROCESSING("PROCESSING"), AUTHORIZED("AUTHORIZED"), PAID("PAID"), REFUNDED("REFUNDED"), WAITING_PAYMENT("WAITING_PAYMENT"),
  PENDING_REFUND("PENDING_REFUND"), REFUSED("REFUSED"), CHARGEDBACK("CHARGEDBACK"), CUSTOMER_BALANCE("CUSTOMER_BALANCE"), ERROR("ERROR");
  private final String paymentStatus;
}
