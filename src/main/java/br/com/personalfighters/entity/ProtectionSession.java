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
@Table(name = "protection_session_")
public class ProtectionSession {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false)
  private Long userId;

  private Integer level;

  private Boolean active;

  private LocalDateTime startedAt;

  private LocalDateTime stoppedAt;

  private LocalDateTime escalatedAt;

  @PrePersist
  private void prePersist() {
    this.startedAt = LocalDateTime.now();
    if (this.level == null) this.level = 1;
    if (this.active == null) this.active = true;
  }
}
