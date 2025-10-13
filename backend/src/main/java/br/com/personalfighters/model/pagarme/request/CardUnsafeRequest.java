package br.com.personalfighters.model.pagarme.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CardUnsafeRequest {

  private String card_number;
  private String card_expiration_date;
  private String card_holder_name;
  private String card_cvv;

  private String customer_id;

  private AddressBilling billing;

}
