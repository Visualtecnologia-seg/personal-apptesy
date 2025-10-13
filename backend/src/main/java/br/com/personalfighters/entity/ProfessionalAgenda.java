package br.com.personalfighters.entity;

import br.com.personalfighters.model.Status;
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
@Table(name = "professional_agenda_")
@JsonView(Views.AgendaView.FirstLevel.class)
public class ProfessionalAgenda {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Enumerated(EnumType.STRING)
  private DayOfWeek dayOfWeek;

  @Enumerated(EnumType.STRING)
  private Status status;

  @Column(columnDefinition = "TEXT")
  private String schedule;

  @ManyToOne
  private User user;

}
