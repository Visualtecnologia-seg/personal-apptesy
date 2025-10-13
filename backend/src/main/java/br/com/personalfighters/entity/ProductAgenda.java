package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.time.DayOfWeek;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_agenda_")
@JsonView(Views.FirstLevel.class)
public class ProductAgenda {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @OneToOne
  private Product product;

  @Enumerated(EnumType.STRING)
  private DayOfWeek dayOfWeek;

  @Column(columnDefinition = "TEXT")
  private String schedule;

}
