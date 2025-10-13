package br.com.personalfighters.entity;

import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.*;

import javax.persistence.*;
import java.text.DecimalFormat;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "professional_")
@JsonView(Views.User.Professional.class)
public class Professional {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(columnDefinition = "TEXT")
  private String about;
  @Column(columnDefinition = "TEXT")
  private String resume;

  private String reference;

  private Double price;
  private Double rating;
  private Long count;
  private Boolean active;

  @PrePersist
  private void newProfessional() {
    this.active = true;
    this.rating = 0.0;
    this.count = 0L;
    this.price = 70.0;
    this.about = "";
    this.reference = "";
  }

  @PostPersist
  private void createReference() {
    String year = String.valueOf(LocalDate.now().getYear()).substring(2, 4);
    String month = String.valueOf(LocalDate.now().getMonthValue());
    DecimalFormat df2 = new DecimalFormat("00"); // 2 zeros
    DecimalFormat df3 = new DecimalFormat("00000"); // 3 zeros

    this.reference = year + df2.format(LocalDate.now().getMonthValue()) + this.id;
  }

}
