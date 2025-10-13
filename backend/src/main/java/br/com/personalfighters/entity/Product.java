package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_")
@JsonView({Views.Product.Basic.class})
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @JsonIgnore
  @ManyToMany(fetch = FetchType.LAZY)
  private List<User> professional;

  @JsonView({Views.Product.Default.class})
  @ManyToOne
  private Category category;

  private String name;
  private String urlImage;
  private String about;
  private String equipment;
  private Boolean active;

}
