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

  private BigDecimal totalCost;

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

  @OneToOne
  private Payment payment;

}
