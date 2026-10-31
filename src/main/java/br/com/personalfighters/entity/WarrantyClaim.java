package br.com.personalfighters.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/** Garantia OPUS: pedido de retorno do profissional em até 30 dias após o serviço. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "warranty_claim_")
public class WarrantyClaim {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private Long customerId;

  private Long professionalId;

  @Column(columnDefinition = "TEXT")
  private String reason;

  /** OPEN, SCHEDULED, RESOLVED */
  private String status;

  @Column(columnDefinition = "TEXT")
  private String professionalNote;

  private String scheduledFor;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @PrePersist
  private void prePersist() {
    if (status == null) status = "OPEN";
    if (createdAt == null) createdAt = LocalDateTime.now();
    updatedAt = createdAt;
  }
}
