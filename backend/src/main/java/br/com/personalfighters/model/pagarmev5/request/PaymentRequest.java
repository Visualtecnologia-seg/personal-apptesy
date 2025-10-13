package br.com.personalfighters.model.pagarmev5.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class PaymentRequest {
  private String paymentMethod;
  private CreditCardPaymentRequest creditCard;
  private String currency;
  private List<SplitRequest> split;
  private String gatewayAffiliationId;
  private Integer amount;
  private String customerId;
  private CustomerRequest customer;
  private LinkedHashMap<String, String> metadata;
}
