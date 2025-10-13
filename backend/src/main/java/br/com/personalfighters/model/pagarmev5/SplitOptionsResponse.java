package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class SplitOptionsResponse {
  private boolean liable;
  private boolean chargeProcessingFee;
  private String chargeRemainderFee;
}
