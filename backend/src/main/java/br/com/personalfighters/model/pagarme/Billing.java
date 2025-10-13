package br.com.personalfighters.model.pagarme;

import br.com.personalfighters.model.pagarme.request.AddressBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Billing {

  private String name;

  private AddressBilling address;
}
