package br.com.personalfighters.model.pagarmev5.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SplitRequest {
  private String type;
  private int amount;
  private String recipientId;
  private SplitOptionsRequest options;
  private String splitRuleId;
}
