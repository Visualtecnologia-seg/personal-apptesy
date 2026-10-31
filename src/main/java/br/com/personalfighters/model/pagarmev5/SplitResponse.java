package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SplitResponse {
  private String type;
  private int amount;
  private RecipientResponse recipient;
  private String gatewayId;
  private SplitOptionsResponse options;
  private String id;
}
