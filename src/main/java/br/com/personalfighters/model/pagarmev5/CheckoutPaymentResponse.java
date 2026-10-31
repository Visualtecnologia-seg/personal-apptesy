package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CheckoutPaymentResponse {
  private String id;
  private Integer amount;
  private String defaultPaymentMethod;
  private String successUrl;
  private String paymentUrl;
  private String gatewayAffiliationId;
  private List<String> acceptedPaymentMethods;
  private String status;
  private boolean skipCheckoutSuccessPage;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime canceledAt;
  private boolean customerEditable;
  private CustomerResponse customer;
  private AddressResponse billingAddress;
  private CheckoutCreditCardPaymentResponse creditCard;
  private boolean billingAddressEditable;
  private boolean shippable;
  private LocalDateTime closedAt;
  private LocalDateTime expiresAt;
  private String currency;
  private List<String> acceptedBrands;
}
