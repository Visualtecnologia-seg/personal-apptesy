package br.com.personalfighters.entity;

import br.com.personalfighters.model.AccountType;
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
@Entity
@Table(name = "bankdata_")
@JsonView(Views.ProfessionalView.FirstLevel.class)
public class BankData {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne
  private User professional;

  private String bank;

  private String agency;

  private String account;

  private String pixEmail;
  private String pixPhoneNumber;
  private String pixCpf;

  @Enumerated(EnumType.STRING)
  private AccountType accountType;
}
