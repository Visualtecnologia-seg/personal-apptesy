package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Card {

  private String object;

  private String id;

  @JsonProperty("date_created")
  private String dateCreated;

  @JsonProperty("date_updated")
  private String dateUpdated;

  private String brand;

  @JsonProperty("holder_name")
  private String holderName;

  @JsonProperty("first_digits")
  private String firstDigits;

  @JsonProperty("last_digits")
  private String lastDigits;

  private String country;

  private String fingerprint;

  private CustomerPagarme customer;

  private boolean valid;

  private String expirationDate;
}
