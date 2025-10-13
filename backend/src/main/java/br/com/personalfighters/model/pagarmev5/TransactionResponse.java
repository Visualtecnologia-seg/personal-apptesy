package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TransactionResponse {
  private String gatewayId;
  private int amount;
  private String status;
  private boolean success;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private int attemptCount;
  private int maxAttempts;
  private List<SplitResponse> splits;
  private LocalDateTime nextAttempt;
  private String transactionType;
  private String acquirerName;
  private String acquirerAffiliationCode;
  private String acquirerTid;
  private String acquirerNsu;
  private String acquirerAuthCode;
  private String acquirerMessage;
  private String acquirerReturnCode;
  private String id;
  private GatewayResponse gatewayResponse;
  private AntifraudResponse antifraudResponse;
  private LinkedHashMap<String, String> metadata;
  private List<SplitResponse> split;
}
