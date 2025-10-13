package br.com.personalfighters.service.impl.exceptions;

public class PaymentException extends RuntimeException {
  public PaymentException(String msg) {
    super(msg);
  }

  public PaymentException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
