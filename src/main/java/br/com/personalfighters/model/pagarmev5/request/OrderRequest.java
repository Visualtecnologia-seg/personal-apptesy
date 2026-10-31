package br.com.personalfighters.model.pagarmev5.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class OrderRequest {
  private List<OrderItemRequest> items;
  private CustomerRequest customer;
  private List<PaymentRequest> payments;
  private String code;
  private String customerId;
  private LinkedHashMap<String, String> metadata;
  private Boolean antifraudEnabled;
  private String ip;
  private String sessionId;
  private LocationRequest location;
  private DeviceRequest device;
  private boolean closed;
  private String currency;
}
