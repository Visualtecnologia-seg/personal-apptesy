package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class AutomaticAnticipationResponse {
  private boolean enabled;
  private String type;
  private int volumePercentage;
  private int delay;
  private List<Integer> days;
}
