package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BillingAddressResponse {
  private String street;
  private String number;
  private String zipCode;
  private String neighborhood;
  private String city;
  private String state;
  private String country;
  private String complement;

  @JsonProperty(value = "line_1")
  private String line1;

  @JsonProperty(value = "line_2")
  private String line2;
}
