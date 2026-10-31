package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CustomerPagarme {

  private String object;

  private String id;

  @JsonProperty("external_id")
  private String externalId;

  private String type;

  private String country;

  //    @JsonProperty("document_number")
  @JsonIgnore
  private String documentNumber;

  @JsonProperty("document_type")
  private String documentType;

  private String name;

  private String email;

  @JsonProperty("phone_numbers")
  private List<String> phoneNumbers;

  //    @JsonProperty("born_at")
  @JsonIgnore
  private String bornAt;

  private String birthday;

  @JsonIgnore
  private String gender;

  @JsonProperty("date_created")
  private String dateCreated;

  private List<Document> documents;

  @JsonIgnore
  private List<AddressPagarme> addresses;
  @JsonIgnore
  private List<String> phones;
}
