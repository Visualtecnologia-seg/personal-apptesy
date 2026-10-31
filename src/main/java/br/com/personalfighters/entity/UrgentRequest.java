package br.com.personalfighters.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Chamado urgente: enviado a todos os profissionais próximos; o primeiro que aceitar fica com ele. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "urgent_request_")
public class UrgentRequest {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private Long customerId;

  private String categoryName;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(columnDefinition = "TEXT")
  private String address;

  private Double latitude;
  private Double longitude;

  /** OPEN, TAKEN, CANCELLED, EXPIRED */
  private String status;

  private Long takenBy;
  private LocalDateTime createdAt;
  private LocalDateTime takenAt;
  private LocalDateTime expiresAt;

  @PrePersist
  private void prePersist() {
    if (status == null) status = "OPEN";
    if (createdAt == null) createdAt = LocalDateTime.now();
    if (expiresAt == null) expiresAt = createdAt.plusMinutes(30);
  }
}
