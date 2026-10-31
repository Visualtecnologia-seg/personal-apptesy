package br.com.personalfighters.model.pagarme.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CardRequest {

  @JsonProperty("card_hash")
  private String cardHash;

  @JsonProperty("customer_id")
  private String customerId;
}
