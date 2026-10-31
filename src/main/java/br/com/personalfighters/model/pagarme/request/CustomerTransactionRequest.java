package br.com.personalfighters.model.pagarme.request;

import br.com.personalfighters.model.pagarme.CustomerPagarme;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CustomerTransactionRequest {

  private String id;

  @JsonProperty("external_id")
  private String externalId;

  private String name;

  private String type;

  private String country;

  private String email;

  @JsonProperty("phone_numbers")
  private List<String> phoneNumbers;


  public CustomerTransactionRequest(CustomerPagarme customerPagarme) {
    this.id = customerPagarme.getId();
    this.externalId = customerPagarme.getExternalId();
    this.name = customerPagarme.getName();
    this.type = customerPagarme.getType();
    this.country = customerPagarme.getCountry();
    this.email = customerPagarme.getEmail();
    this.phoneNumbers = customerPagarme.getPhoneNumbers();
  }
}
