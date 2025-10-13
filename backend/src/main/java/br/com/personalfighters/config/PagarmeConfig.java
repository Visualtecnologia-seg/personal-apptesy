package br.com.personalfighters.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PagarmeConfig {

  @Value("${pagarme.key}")
  private String username;

  @Value("${pagarme.url}")
  private String url;

  @Bean
  public WebClient pagarmeClient(WebClient.Builder webClientBuilder) {
    return webClientBuilder.baseUrl(url)
        .filter(ExchangeFilterFunctions
            .basicAuthentication(username, "x"))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
