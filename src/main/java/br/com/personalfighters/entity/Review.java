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

  @ManyToOne
  @JoinColumn(name = "reviewer_id")
  private User reviewer;

  @ManyToOne
  private Order order;

  private String type;

  private Integer rating;
  private LocalDate date;
  private String comment;

  private Double cat1;
  private Double cat2;
  private Double cat3;
  private Double cat4;

  private Double tipAmount;

  private Boolean revealed;

  /** TESTIMONY | PHOTOS | BEFORE_AFTER | VIDEO_SHORT | VIDEO_BEFORE_AFTER */
  @Column(name = "media_type", length = 50)
  private String mediaType;

  /** URLs separadas por vírgula ou JSON de URIs */
  @Column(name = "media_urls", columnDefinition = "TEXT")
  private String mediaUrls;

  /** CLIENT | PROFESSIONAL */
  @Column(name = "reviewer_role", length = 20)
  private String reviewerRole;

  /** Elogios rápidos escolhidos pelo cliente, separados por "|" (ex.: "Pontual|Limpou tudo depois"). */
  @Column(name = "tags", columnDefinition = "TEXT")
  private String tags;
}
