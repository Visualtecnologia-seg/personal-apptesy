package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class BankAccountResponse {
  private String id;
  private String holderName;
  private String holderType;
  private String bank;
  private String branchNumber;
  private String branchCheckDigit;
  private String accountNumber;
  private String accountCheckDigit;
  private String type;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;
  private RecipientResponse recipient;
  private LinkedHashMap<String, String> metadata;
  private String pixKey;
}
