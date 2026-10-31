package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

  Page<Order> findByProfessionalAndStatusIn(User professional, List<Status> status, Pageable pageable);

  //TODO remove or change to ProfessionalRepository
  @Query("select oa from Order as o inner join o.available oa where o.status = :status and o.id = :id")
  List<User> findProfessionalByOrderAndStatus(Status status, UUID id);

  @Query("select o from Order o inner join o.available oa where oa.professional.id =:id")
  Page<Order> findByProfessional(Long id, Pageable pageable);

  @Query("select o from Order as o where o.professional = :professional and o.status = :status and o.date between :startDate and :endDate")
  Iterable<Order> findByProfessionalAndConfirmed(User professional, Status status, LocalDate startDate, LocalDate endDate);

  @Query("select o from Order as o inner join o.response as r where r = :professional and o.date between :startDate and :endDate")
  Iterable<Order> findByProfessionalAndResponded(User professional, LocalDate startDate, LocalDate endDate);

  Page<Order> findByCustomer(User customer, Pageable pageable);

  List<Order> findAllByCustomer(User customer);

  Page<Order> findByCustomerAndStatusInOrderByStatusDescDateAsc(User customer, List<Status> status, Pageable pageable);

  @Query("select oa from Order as o inner join o.available oa where o.hasSent =:hasSent group by oa")
  Iterable<User> findNonNotifiedProfessionals(boolean hasSent);

  Iterable<Order> findByHasSent(boolean hasSent);

  @Query("select count(o) from Order as o inner join o.response as res where (o.professional = :professional or res = :professional) and o.date = :date and o.startTime = :startTime")
  Long checkIfProfessionalAgendaIsAvailable(User professional, LocalDate date, LocalTime startTime);

  @Query("select count(o) from Order as o where o.customer = :customer and o.date = :date and (o.startTime = :startTime or o.startTime = :endTime) " +
      "and (o.status = 'OPEN' or o.status = 'BLOCKED' or o.status = 'CONFIRMED')")
  Long checkIfCustomerAgendaIsAvailable(User customer, LocalDate date, LocalTime startTime, LocalTime endTime);

  Iterable<Order> findOrdersByCreatedAtBeforeAndStatus(LocalDateTime time, Status status);

  Iterable<Order> findOrdersByDateAndStartTimeAndStatus(LocalDate date, LocalTime time, Status status);

  List<Order> findOrdersByDateAndStartTimeAndAvailableContaining(LocalDate date, LocalTime startTime, User professional);

  List<Order> findOrdersByDateIsLessThanEqualAndStartTimeIsLessThanEqualAndStatus(LocalDate date, LocalTime time, Status status);

  /**
   * Finds DONE orders eligible for automatic escrow release:
   * paid but not yet released, no open dispute, created more than {threshold} ago.
   */
  @Query("select o from Order o where o.status = ?1 and o.pixPaid = true " +
      "and (o.pixReleased is null or o.pixReleased = false) " +
      "and (o.disputeStatus is null or o.disputeStatus != 'OPEN') " +
      "and o.createdAt < ?2")
  List<Order> findDoneOrdersEligibleForAutoRelease(Status status, LocalDateTime threshold);

  List<Order> findByStatus(Status status);
}
