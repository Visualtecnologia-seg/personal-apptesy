package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@Entity
@Table(name = "credit_card_")
@JsonView({Views.FinanceView.FirstLevel.class})
public class CreditCard {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @JsonIgnore
  @ManyToOne
  private User user;

  private String paymentServiceId;
  private String name;
  private String lastNumbers;
  private String brand;
  private Boolean defaultCard;
  private String street;
  private String streetNumber;
  private String neighborhood;
  private String zipcode;
  private String city;
  private String state;

  //Duas letras minúsculas. Deve seguir o padão ISO 3166-1 alpha-2
  private String country;

}
