package br.com.personalfighters.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Envio de SMS. Usa a Twilio quando TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN e TWILIO_FROM estão no servidor.
 * Sem essas chaves, fica em MODO DE TESTE: nada é enviado e o código aparece na tela (só para demonstração).
 */
@Slf4j
@Service
public class SmsService {

  @Value("${TWILIO_ACCOUNT_SID:}") private String sid;
  @Value("${TWILIO_AUTH_TOKEN:}") private String token;
  @Value("${TWILIO_FROM:}") private String from;

  public boolean isTestMode() {
    return sid == null || sid.isBlank() || token == null || token.isBlank() || from == null || from.isBlank();
  }

  /** Envia o SMS. Telefone no formato +55DDDNÚMERO. Devolve true se saiu. */
  public boolean send(String e164, String text) {
    if (isTestMode()) {
      log.info("SMS em modo de teste para {}", e164.replaceAll("\\d(?=\\d{4})", "*"));
      return true;
    }
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("To", e164);
      form.add("From", from);
      form.add("Body", text);
      String auth = Base64.getEncoder().encodeToString((sid + ":" + token).getBytes(StandardCharsets.UTF_8));
      WebClient.create("https://api.twilio.com").post()
          .uri("/2010-04-01/Accounts/" + sid + "/Messages.json")
          .header("Authorization", "Basic " + auth)
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body(BodyInserters.fromFormData(form))
          .retrieve().bodyToMono(String.class).block();
      return true;
    } catch (Exception e) {
      log.error("SMS não enviado: {}", e.getMessage());
      return false;
    }
  }
}
