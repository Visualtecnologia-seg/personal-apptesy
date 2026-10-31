package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Item {

  @JsonIgnore
  private String object;

  private String id;

  private String title;

  @JsonProperty("unit_price")
  private Integer unitPrice;

  private Number quantity;

  private String category;

  private boolean tangible;

  private String venue;

  private String date;
}
