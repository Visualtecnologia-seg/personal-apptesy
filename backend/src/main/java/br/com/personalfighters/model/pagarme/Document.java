package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class Document {

  @JsonIgnore
  private String object;

  //    @JsonIgnore
  private String id;

  private String type;

  private String number;
}
