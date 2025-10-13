package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class RecipientResponse {
  private String id;
  private String name;
  private String email;
  private String document;
  private String description;
  private String type;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime deletedAt;
  private BankAccountResponse defaultBankAccount;
  private List<GatewayRecipientResponse> gatewayRecipients;
  private LinkedHashMap<String, String> metadata;
  private AutomaticAnticipationResponse automaticAnticipationSettings;
  private TransferSettingsResponse transferSettings;
  private String code;
  private String paymentMode;
}
