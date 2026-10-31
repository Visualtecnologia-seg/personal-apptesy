package br.com.personalfighters.model.pagarmev5.request;

import br.com.personalfighters.model.pagarme.request.AddressBilling;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AddressRequest {

  private String zipCode;
  private String city;
  private String state;
  private String country;
  private LinkedHashMap<String, String> metadata;

  @JsonProperty(value = "line_1")
  private String line1;

  @JsonProperty(value = "line_2")
  private String line2;

  public static AddressRequest convertToAddressRequest(AddressBilling billing) {
    return AddressRequest.builder()
        .zipCode(billing.getZipcode().replace("-", ""))
        .line1(
            billing.getStreet_number()
                + ", "
                + billing.getStreet()
                + ", "
                + billing.getNeighborhood())
        .line2("") // Frontend não está enviando complemento
        .city(billing.getCity())
        .state(billing.getState())
        .country(billing.getCountry())
        .build();
  }
}
