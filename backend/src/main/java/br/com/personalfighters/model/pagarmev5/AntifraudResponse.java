package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AntifraudResponse {
  private String status;
  private String returnCode;
  private String returnMessage;
  private String providerName;
  private String score;
}
