package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum PaymentStatusReason {
  ACQUIRER("ACQUIRER"), ANTIFRAUD("ANTIFRAUD"), INTERNAL_ERROR("INTERNAL_ERROR"), NO_ACQUIRER("NO_ACQUIRER"),
  ACQUIRER_TIMEOUT("ACQUIRER_TIMEOUT"), PAYMENT_AUTHORIZED_AMOUNT_ERROR("PAYMENT_AUTHORIZED_AMOUNT_ERROR");
  private final String paymentStatusReason;
}
