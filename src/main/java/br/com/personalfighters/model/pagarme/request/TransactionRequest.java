package br.com.personalfighters.model.pagarme.request;

import br.com.personalfighters.model.pagarme.Billing;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TransactionRequest {

  private Integer amount;

  @JsonProperty("card_id")
  private String cardId;

  @JsonProperty("customer_id")
  private String customerId;

  private Billing billing;

  private String capture;
}
