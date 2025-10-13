package br.com.personalfighters.service.impl.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class PagarmeProperties {

  @Value("${pagarme.url}")
  private String url;

  @Value("${pagarme.customers-endpoint}")
  private String customersEndpoint;

  @Value("${pagarme.customers-cards-endpoint}")
  private String customersCardsEnpoint;

  @Value("${pagarme.orders-endpoint}")
  private String ordersEndpoint;

  @Value("${pagarme.charges-capture-endpoint}")
  private String ordersChargeCaptureEndpoint;

  @Value("${pagarme.charges-refund-endpoint}")
  private String ordersChargeRefundEndpoint;
}
