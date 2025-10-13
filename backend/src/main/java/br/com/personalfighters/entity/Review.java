package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "review_")
@JsonView(Views.FirstLevel.class)
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @ManyToOne
  private User customer;

  @ManyToOne
  private User professional;

  @OneToOne
  private Order order;

  private Integer rating;
  private LocalDate date;
  private String comment;

}
