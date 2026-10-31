package br.com.personalfighters.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido de verificação de identidade enviado pelo app.
 * As imagens ficam guardadas aqui (data URL base64) e nunca saem
 * junto com o usuário: só o administrador acessa por /verifications/{id}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "identity_verification_")
public class IdentityVerification {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private Long userId;

  /** PENDING, APPROVED ou REJECTED */
  private String status;

  /** RG ou CNH */
  private String docType;

  @Column(columnDefinition = "TEXT")
  private String docFront;

  @Column(columnDefinition = "TEXT")
  private String docBack;

  @Column(columnDefinition = "TEXT")
  private String selfieNeutral;

  /** Prova de vida: foto com o rosto virado para um lado e para o outro (detectado no celular). */
  @Column(name = "selfie_turn_a", columnDefinition = "TEXT")
  private String selfieTurnA;

  @Column(name = "selfie_turn_b", columnDefinition = "TEXT")
  private String selfieTurnB;

  /** true quando o app confirmou, ao vivo, que a pessoa virou a cabeça para os dois lados e piscou. */
  private Boolean livenessPassed;

  /** Ângulos medidos pelo app (ex.: "yawA=27.5;yawB=-30.1;blink=1"), para conferência. */
  private String livenessInfo;

  @Column(columnDefinition = "TEXT")
  private String selfieWithDoc;

  private String consentVersion;
  private LocalDateTime consentAt;
  private LocalDateTime submittedAt;
  private LocalDateTime reviewedAt;
  private String reviewedBy;

  @Column(columnDefinition = "TEXT")
  private String rejectionReason;

  /** Etapas a refazer, separadas por vírgula: DOC_FRONT, DOC_BACK, FACE, SELFIE_DOC */
  private String rejectedSteps;

  @PrePersist
  private void prePersist() {
    if (this.status == null) this.status = "PENDING";
    if (this.submittedAt == null) this.submittedAt = LocalDateTime.now();
  }
}
