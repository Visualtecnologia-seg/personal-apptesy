package br.com.personalfighters.entity;

import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.Status;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_")
public class Order {

  @Id
  @GeneratedValue
  @Setter(AccessLevel.NONE)
  private UUID id;

  private LocalDateTime createdAt;

  private LocalDate date;

  private LocalTime startTime;

  private LocalTime endTime;

  @Enumerated(EnumType.STRING)
  private Status status;

  private Integer numberOfCustomers;

  private Integer hours;

  private String serviceAddress;

  private BigDecimal totalCost;

  /** Valor do serviço que vai para o profissional (sem a taxa OPUS). */
  @Column(name = "service_value")
  private BigDecimal serviceValue;

  /** Código de 6 números da conclusão — nunca vai no JSON. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "finish_code")
  private String finishCode;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "finish_code_expires_at")
  private java.time.LocalDateTime finishCodeExpiresAt;

  @Column(name = "payment_released_at")
  private java.time.LocalDateTime paymentReleasedAt;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "points_awarded")
  private Boolean pointsAwarded;

  @Enumerated(EnumType.STRING)
  private Gender gender;

  private Boolean hasSent;

  @ManyToOne
  private Address address;

  @ManyToOne
  private Product product;

  @ManyToOne
  private User customer;

  @ManyToOne
  private User professional;

  @ManyToMany(fetch = FetchType.LAZY)
  private List<User> response;

  @ManyToMany(fetch = FetchType.LAZY)
  private List<User> available;

  private Boolean isReviewed;
  private Boolean clientReviewed;
  private Boolean proReviewed;

  private String pixChargeId;
  private Boolean pixPaid;
  private Boolean proConfirmedArrival;
  private Boolean clientConfirmedArrival;
  private Boolean pixReleased;

  private java.time.LocalDateTime requestedCompletionAt;

  @OneToOne
  private Payment payment;

  private String disputeReason;
  private String disputeStatus;
  private LocalDateTime disputedAt;

  private String cancelledBy;

  /** HOUR (por hora, padrão) ou SERVICE (valor fechado combinado no chat). */
  private String pricingMode;
  private String cancelReason;

  /** Desconto aplicado com pontos (sai da parte da plataforma). */
  private BigDecimal discountAmount;

  /** Quando o serviço foi concluído — conta o prazo da garantia de 30 dias. */
  private LocalDateTime doneAt;

  /** Pedido criado a partir de um chamado urgente. */
  private Boolean urgent;
}
