package br.com.personalfighters.model.pagarmev5;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class OrderResponse {
  private String id;
  private String code;
  private String currency;
  private List<OrderItemResponse> items;
  private CustomerResponse customer;
  private String status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private List<ChargeResponse> charges;
  private String invoiceUrl;
  private LinkedHashMap<String, String> metadata;
  private List<CheckoutPaymentResponse> checkouts;
  private String ip;
  private String sessionId;
  private LocationResponse location;
  private DeviceResponse device;
  private boolean closed;
}
