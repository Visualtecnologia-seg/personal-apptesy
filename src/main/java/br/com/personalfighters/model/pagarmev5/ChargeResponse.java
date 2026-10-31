package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ChargeResponse {
  private String id;
  private String code;
  private String gatewayId;
  private int amount;
  private String status;
  private String currency;
  private String paymentMethod;
  private LocalDateTime dueAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private TransactionResponse lastTransaction;
  private OrderResponse order;
  private CustomerResponse customer;
  private LinkedHashMap<String, String> metadata;
  private LocalDateTime paidAt;
  private LocalDateTime canceledAt;
  private int canceledAmount;
  private int paidAmount;
}
