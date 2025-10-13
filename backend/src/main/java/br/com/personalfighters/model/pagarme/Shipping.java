package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Shipping {

  private String name;

  private Number fee;

  @JsonProperty("delivery_date")
  private String deliveryDate;

  private boolean expedited;

  private AddressPagarme address;
}
