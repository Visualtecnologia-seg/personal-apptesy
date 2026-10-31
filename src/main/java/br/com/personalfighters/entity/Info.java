package br.com.personalfighters.entity;

import br.com.personalfighters.model.Auditable;
import lombok.*;

import javax.persistence.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "info_")
public class Info extends Auditable<String> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Setter(AccessLevel.NONE)
  private Long id;

  @Column(columnDefinition = "TEXT")
  private String content;

  private String title;
  private String imageUrl;
  private Boolean active;

}