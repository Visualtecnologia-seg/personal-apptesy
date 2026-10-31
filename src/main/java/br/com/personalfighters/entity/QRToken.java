package br.com.personalfighters.entity;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_token_")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QRToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private UUID orderId;

  @Column(nullable = false, unique = true, length = 10)
  private String code;

  @Column(nullable = false, length = 30)
  private String type; // ARRIVAL or COMPLETION

  @Column(nullable = false)
  private boolean used = false;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = LocalDateTime.now();
  }
}
