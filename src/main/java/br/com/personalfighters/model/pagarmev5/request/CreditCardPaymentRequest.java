package br.com.personalfighters.model.pagarmev5.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreditCardPaymentRequest {
  private Integer installments;
  private String statementDescriptor;
  private CreateCardRequest card;
  private String cardId;
  private String cardToken;
  private Boolean recurrence;
  private Boolean extendedLimitEnabled;
  private String extendedLimitCode;
  private Long merchantCategoryCode;
  //  private PaymentAuthenticationRequest authentication;
  //  private CardPaymentContactlessRequest contactless;
  private Boolean autoRecovery;
  private String operationType;
}
