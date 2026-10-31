package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CustomerResponse {

  private String id;
  private String name;
  private String email;
  private boolean delinquent;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String document;
  private String type;
  private String fbAccessToken;
  private AddressResponse address;
  private LinkedHashMap<String, String> metadata;
  private PhonesResponse phones;
  private Long fbId;
  private String code;
  private String documentType;
}
