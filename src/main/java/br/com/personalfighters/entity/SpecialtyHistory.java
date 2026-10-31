package br.com.personalfighters.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "specialty_history_")
public class SpecialtyHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(columnDefinition = "TEXT")
  private String specialties;

  @Column(name = "changed_at", nullable = false)
  private LocalDateTime changedAt;

  @PrePersist
  private void prePersist() {
    if (this.changedAt == null) this.changedAt = LocalDateTime.now();
  }
}
