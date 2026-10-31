package br.com.personalfighters.model.pagarme.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressBilling {

  private String street;
  private String street_number;
  private String neighborhood;
  private String zipcode;
  private String city;
  private String state;
  private String country;
}
