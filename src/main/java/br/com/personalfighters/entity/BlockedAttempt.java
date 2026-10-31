package br.com.personalfighters.entity;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "blocked_attempt_")
public class BlockedAttempt {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  private Long senderId;

  private String senderRole;

  private String matchedPattern;

  @Column(columnDefinition = "TEXT")
  private String originalText;

  private Integer attemptNumber;

  private LocalDateTime blockedAt;

  @PrePersist
  private void prePersist() {
    this.blockedAt = LocalDateTime.now();
  }
}
