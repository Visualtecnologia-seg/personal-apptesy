package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CardResponse {
  private String id;
  private String lastFourDigits;
  private String brand;
  private String holderName;
  private int expMonth;
  private int expYear;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private BillingAddressResponse billingAddress;
  private CustomerResponse customer;
  private LinkedHashMap<String, String> metadata;
  private String type;
  private String holderDocument;
  private LocalDateTime deletedAt;
  private String firstSixDigits;
  private String label;
}
