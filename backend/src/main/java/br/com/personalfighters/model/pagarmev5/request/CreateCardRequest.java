package br.com.personalfighters.model.pagarmev5.request;

import br.com.personalfighters.model.pagarme.request.CardUnsafeRequest;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CreateCardRequest {
  private String number;
  private String holderName;
  private int expMonth;
  private int expYear;
  private String cvv;
  private AddressRequest billingAddress;
  private String brand;
  private String billingAddressId;
  private LinkedHashMap<String, String> metadata;
  private String type;
  private CardOptionsRequest options;
  private String holderDocument;
  private boolean privateLabel;
  private String label;
  private String id;
  private String token;

  public static CreateCardRequest convertToCardRequest(CardUnsafeRequest unsafeRequest) {
    return CreateCardRequest.builder()
        .number(unsafeRequest.getCard_number())
        .holderName(unsafeRequest.getCard_holder_name())
        .cvv(unsafeRequest.getCard_cvv())
        .expMonth(Integer.parseInt(unsafeRequest.getCard_expiration_date().substring(0, 2)))
        .expYear(Integer.parseInt(unsafeRequest.getCard_expiration_date().substring(2)))
        .billingAddress(AddressRequest.convertToAddressRequest(unsafeRequest.getBilling()))
        .build();
  }
}
