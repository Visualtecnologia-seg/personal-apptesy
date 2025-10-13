package br.com.personalfighters.model.pagarmev5.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CancelChargeRequest {
  private Integer amount;
  private List<CancelChargeSplitRulesRequest> splitRules;
  private List<SplitRequest> split;
  private String operationReference;
}
