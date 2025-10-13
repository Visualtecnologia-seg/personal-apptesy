package br.com.personalfighters.model.pagarme.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerPagarmeRequest {

  @JsonProperty("external_id")
  private String externalId;

  private String name;

  private String type;

  private String country;

  private String email;

  private String birthday;

  private List<DocumentRequest> documents;

  @JsonProperty("phone_numbers")
  private List<String> phoneNumbers;
}
