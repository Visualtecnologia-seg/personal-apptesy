package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AddressPagarme {

  private String street;

  @JsonProperty("street_number")
  private String streetNumber;

  private String complementary;

  private String neighborhood;

  private String zipcode;

  private String city;

  private String state;

  private String country;
}
