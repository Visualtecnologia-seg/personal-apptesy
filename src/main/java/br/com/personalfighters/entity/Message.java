package br.com.personalfighters.entity;

import lombok.*;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "message_")
public class Message {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  @Column(nullable = false)
  private UUID orderId;

  private Long senderId;
  private String senderName;
  private String senderRole; // PROFESSIONAL | CUSTOMER

  @Column(columnDefinition = "TEXT")
  private String text;

  @Column(columnDefinition = "TEXT")
  private String imageUrl;

  private String messageType; // TEXT | IMAGE | QUOTE | SYSTEM

  // Quote status: PENDING | NEGOTIATING | ACCEPTED | REJECTED
  private String quoteStatus;
  private BigDecimal quoteAmount;   // kept for legacy compatibility
  private String quoteDescription;
  private Integer quoteHours;

  // Quote breakdown (new)
  @Column(columnDefinition = "TEXT")
  private String quoteItemsJson;      // JSON array of QuoteItem objects
  private BigDecimal quoteServiceCost;
  private BigDecimal quoteMaterialsCost;
  private BigDecimal quoteTotalCost;

  // Counter offer (1 round)
  private BigDecimal counterOfferAmount;
  @Column(columnDefinition = "TEXT")
  private String counterOfferNote;
  private String counterOfferStatus;  // PENDING_PRO | ACCEPTED_BY_PRO | REJECTED_BY_PRO

  // Client checklist: JSON array of item IDs the client already bought
  @Column(columnDefinition = "TEXT")
  private String clientCheckedItems;

  private LocalDateTime createdAt;

  @PrePersist
  private void prePersist() {
    this.createdAt = LocalDateTime.now();
    if (this.messageType == null) this.messageType = "TEXT";
    if ("QUOTE".equals(this.messageType) && this.quoteStatus == null) {
      this.quoteStatus = "PENDING";
    }
  }
}
