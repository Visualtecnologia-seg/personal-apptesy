package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CardHashKey {

  @JsonProperty("date_created")
  private String dateCreated;

  private Integer id;

  private String ip;

  @JsonProperty("public_key")
  private String publicKey;
}
