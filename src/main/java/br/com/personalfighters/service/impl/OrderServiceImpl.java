package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.FinanceRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.PaymentRepository;
import br.com.personalfighters.repository.PointsRepository;
import br.com.personalfighters.repository.ProductRepository;
import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.OrderService;
import br.com.personalfighters.service.TransactionService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import br.com.personalfighters.service.impl.exceptions.PaymentException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
@AllArgsConstructor
public class OrderServiceImpl implements OrderService {

  private final UserService userService;
  private final OrderRepository orderRepository;
  //  private final ProfessionalAgendaService professionalAgendaService;
  private final ProductRepository productRepository;
  private final FinanceRepository financeRepository;

  private final PaymentRepository paymentRepository;

  private final NotificationService notificationService;
  private final TransactionService transactionService;
  private final EmailService emailService;
  private final PointsRepository pointsRepository;

  private final ZoneId zoneId = ZoneId.of("America/Sao_Paulo");

  @Override
  public Order findById(UUID id) {
    return orderRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("Ordem não encontrada - ID: " + id));
  }

  @Override
  public Page<Order> findAll(Order order, Pageable pageable) {
    Example<Order> example = Example.of(order,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING));
    return orderRepository.findAll(example, pageable);
  }

  @Override
  @Transactional
  public Order save(Order order) {
    if (order.getDate() != null && order.getDate().isBefore(LocalDate.now(zoneId))) {
      throw new RuntimeException("Não é possível criar pedidos para datas passadas");
    }
    if (order.getDate() != null && order.getDate().isAfter(LocalDate.now(zoneId).plusYears(1))) {
      throw new RuntimeException("A data do pedido não pode ser superior a 1 ano");
    }
    if (order.getPayment() != null) {
      Payment payment = paymentRepository.save(order.getPayment());
      order.setPayment(payment);
    }

    if (order.getDate() != null && order.getStartTime() != null && order.getEndTime() != null) {
      boolean customerAgendaIsNotAvailable = orderRepository.checkIfCustomerAgendaIsAvailable(
          order.getCustomer(), order.getDate(), order.getStartTime(), order.getEndTime()) > 0;
      if (customerAgendaIsNotAvailable) {
        throw new RuntimeException("Horário indisponível. Já existe um pedido nesse horário");
      }
    }
    order.setCreatedAt(LocalDateTime.now(zoneId));
    order.setIsReviewed(false);
    order.setPixPaid(false);
    order.setProConfirmedArrival(false);
    order.setClientConfirmedArrival(false);
    order.setPixReleased(false);
    order.setAvailable(findAllAvailableProfessionals(order));
    orderRepository.save(order);

    if (!ObjectUtils.isEmpty(order.getProfessional())) {
      notificationService.sendConfirmationToProfessional(order.getCustomer(), order.getProfessional(), order);
    } else {
      notificationService.sendNotificationToProfessionalsOnOrder(order);
    }
    // Delay de disparo de notificações
//    if (order.getDate().equals(LocalDate.now(zoneId)) && startTime - now > 0 && startTime - now <= 3) {
//      notificationService.sendNotificationToProfessionalsOnOrder(order);
//    }
    return order;
  }

  @Override
  @Transactional
  public void confirmByProfessional(UUID id, Long professionalId) {
    User professional = userService.findById(professionalId);
    Order order = orderRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));

    if (!professional.getActive()) {
      throw new RuntimeException("Profissional está inativo");
    } else if (order.getStatus().equals(Status.BLOCKED)) {
      throw new RuntimeException("Número máximo de profissionais já confirmado");
    } else if (order.getResponse().contains(professional)) {
      throw new RuntimeException("Profissional já está confirmado");
    }

    /* Verificação de agendamento direto na agenda do profissional */
    if (!ObjectUtils.isEmpty(order.getProfessional()) && order.getProfessional().equals(professional)) {
      order.setAvailable(new ArrayList<>());
      order.setResponse(new ArrayList<>());
      order.setStatus(Status.CONFIRMED);
      orderRepository.save(order);
    } else {
      List<User> professionals = order.getResponse();
      professionals.add(professional);
      order.setResponse(professionals);

      List<User> freeProfessionals = orderRepository.findProfessionalByOrderAndStatus(Status.OPEN, order.getId());
      freeProfessionals = freeProfessionals
          .stream()
          .filter(p -> !p.getId().equals(professional.getId()))
          .collect(Collectors.toList());
      order.setAvailable(freeProfessionals);

      if (professionals.size() == 5) {
        order.setStatus(Status.BLOCKED);
      }
    }

    notificationService.sendConfirmationToCustomer(order.getCustomer(), professional);

    /* Remove o profissional de outros pedidos no mesmo dia e horário */
    List<Order> list = orderRepository.findOrdersByDateAndStartTimeAndAvailableContaining(
        order.getDate(), order.getStartTime(), professional);

    /* TODO Verificar se o profissional é removido de todas as listas */
    list.forEach(item -> item.setAvailable(item.getAvailable()
        .stream()
        .filter(p -> !p.getId().equals(professional.getId()))
        .collect(Collectors.toList())));
    orderRepository.saveAll(list);
    orderRepository.save(order);
  }

  @Override
  @Transactional
  public void confirmByCustomer(UUID uuid, Long id) {
    User professional = userService.findById(id);

    if (!professional.getActive()) {
      throw new RuntimeException("Profissional está inativo");
    }

    Order order = orderRepository.findById(uuid).orElseThrow();

    order.setAvailable(new ArrayList<>());
    order.setResponse(new ArrayList<>());
    order.setStatus(Status.CONFIRMED);
    order.setProfessional(professional);
    orderRepository.save(order);
    notificationService.sendConfirmationToProfessional(order.getCustomer(), order.getProfessional(), order);
  }

  @Override
  @Transactional
  public void rejectByProfessional(UUID id, Long professionalId) {
    User professional = userService.findById(professionalId);
    Order order = orderRepository.findById(id).orElseThrow();
    List<User> available = order.getAvailable().stream()
        .filter(p -> !p.getId().equals(professional.getId())).collect(Collectors.toList());
    order.setAvailable(available);
    orderRepository.save(order);
  }

  @Override
  @Transactional
  public void cancelOrder(Order o, Role role) {
    Order order = orderRepository.findById(o.getId()).orElseThrow();
    order.setAvailable(new ArrayList<>());
    order.setResponse(new ArrayList<>());

    if (order.getStatus().equals(Status.CONFIRMED) || order.getStatus().equals(Status.AWAITING_PAYMENT)) {
      try {
        transactionService.refundPayment(order);
        if (role.equals(Role.PROFESSIONAL)) {
          Finance finance = financeRepository.findByUser(order.getProfessional());
          finance.setProfessionalBalance(finance.getProfessionalBalance().add(new BigDecimal("-3.8")));
          financeRepository.save(finance);
        }
        if (role.equals(Role.CUSTOMER)) {
          Finance finance = financeRepository.findByUser(order.getCustomer());
          finance.setCustomerBalance(finance.getCustomerBalance().add(new BigDecimal("-3.8")));
          financeRepository.save(finance);
        }
      } catch (Exception e) {
        log.info("Erro ao processar estorno do pagamento.");
      }
      notificationService.sendCancellationToCustomerAndProfessional(order);
      try {
        if (order.getCustomer() != null && order.getCustomer().getEmail() != null) {
          emailService.sendOrderCancelled(order.getCustomer().getEmail(), order.getCustomer().getName());
        }
      } catch (Exception e) {
        log.warn("E-mail de cancelamento não enviado: {}", e.getMessage());
      }
    }
    order.setStatus(Status.CANCELLED);
    orderRepository.save(order);
  }


  @Override
  public Order update(Order order) {
    return orderRepository.save(order);
  }

  @Override
  public Page<Order> findByCustomer(Long id, Pageable pageable) {
    User customer = userService.findById(id);
    return orderRepository.findByCustomer(customer, pageable);
  }

  @Override
  public Page<Order> findByCustomerAndStatus(Long id, List<Status> status, Pageable pageable) {
    User customer = userService.findById(id);
    return orderRepository.findByCustomerAndStatusInOrderByStatusDescDateAsc(customer, status, pageable);
  }

  @Override
  public Page<Order> findByProfessionalAndStatus(Long id, List<Status> status, Pageable pageable) {
    User professional = userService.findById(id);
    return orderRepository.findByProfessionalAndStatusIn(professional, status, pageable);
  }

  @Override
  public void cancelOrdersByTimeExpiration() {
    LocalDateTime time = LocalDateTime.now(zoneId).minusHours(12);
    Iterable<Order> iterableOrders = orderRepository.findOrdersByCreatedAtBeforeAndStatus(time, Status.OPEN);
    List<Order> orders = StreamSupport.stream(iterableOrders.spliterator(), false).collect(Collectors.toList());
    orders.forEach(order -> cancelOrder(order, Role.ADMIN));
  }

  @Override
  public void cancelOrdersByMinimumTimeRequired() {
    LocalDate date = LocalDate.now(zoneId);
    LocalTime time = LocalTime.now(zoneId);
    time = time.plusHours(1).minusSeconds(time.getSecond()).minusNanos(time.getNano());
    Iterable<Order> iterableOrders = orderRepository.findOrdersByDateAndStartTimeAndStatus(date, time, Status.OPEN);
    List<Order> orders = StreamSupport.stream(iterableOrders.spliterator(), false).collect(Collectors.toList());
    orders.forEach(order -> cancelOrder(order, Role.ADMIN));
  }

  @Override
  public void setReviewed(UUID id) {
    Order order = findById(id);
    order.setIsReviewed(true);
    orderRepository.save(order);
  }

  @Override
  public void setOrdersToDone() {
    LocalDate date = LocalDate.now(zoneId);
    LocalTime time = LocalTime.now(zoneId);
    time = time.minusHours(1L);
    var orders =
        orderRepository.findOrdersByDateIsLessThanEqualAndStartTimeIsLessThanEqualAndStatus(
            date, time, Status.CONFIRMED);
    orders.forEach(
        order -> {
          order.setStatus(Status.DONE);
          var transactionOpt = transactionService.findByOrderId(order.getId());
          if (transactionOpt.isPresent()) {
            var transaction = transactionOpt.get();
            var finance = financeRepository.findByUser(order.getProfessional());
            var professionalValue =
                finance.getProfessionalBalance().add(transaction.getProfessionalValue());
            finance.setProfessionalBalance(professionalValue.setScale(2, RoundingMode.HALF_UP));
            transaction.setOrderIsDone(true);
            update(order);
            financeRepository.save(finance);
            transactionService.update(transaction);
          } else {
            log.error(
                String.format("Order: %s - Não foi possível achar a transação", order.getId()));
          }
        });
  }

  @Override
  public Page<Order> findByProfessional(Long id, Pageable pageable) {
    return orderRepository.findByProfessional(id, pageable);
  }

  @Override
  public Iterable<User> findNonNotifiedProfessionals(boolean hasSent) {
    return orderRepository.findNonNotifiedProfessionals(hasSent);
  }

  @Override
  public List<User> findAvailable(Order order) {
    return findAllAvailableProfessionals(order);
  }

  @Override
  public Iterable<Order> findByHasSent(boolean hasSent) {
    return orderRepository.findByHasSent(hasSent);
  }

  private List<User> findAllAvailableProfessionals(Order order) {
    List<User> list;
    if (ObjectUtils.isEmpty(order.getProfessional())) {
      list = productRepository.findAllProfessionalsByProduct(order.getProduct());
    } else {
      list = Collections.singletonList(order.getProfessional());
    }

    list = list.stream().filter(user -> user.getProfessional().getActive()).collect(Collectors.toList());

    List<User> professionals = new ArrayList<>();
    list.forEach(professional -> {
      /* Check se o profissional já está com data e horário ocupados */
      boolean hasOrdersWithEqualStartTime = orderRepository
          .checkIfProfessionalAgendaIsAvailable(professional, order.getDate(), order.getStartTime()) == 0;

      /* Check se o profissional tem alguma ordem que termina entre os horários da ordem */
      boolean hasOrdersWithEndTimeBetweenOrderTime = orderRepository
          .checkIfProfessionalAgendaIsAvailable(professional, order.getDate(), order.getStartTime().minusMinutes(30)) == 0;

      /* Check se o profissional está com o ultimo horário ocupado e sem offset de 30 minutos */
      boolean hasOrdersWithoutOffset = orderRepository
          .checkIfProfessionalAgendaIsAvailable(professional, order.getDate(), order.getStartTime().minusMinutes(60)) == 0;

      if (hasOrdersWithEqualStartTime && hasOrdersWithEndTimeBetweenOrderTime && hasOrdersWithoutOffset && !professional.getId().equals(order.getCustomer().getId())) {
        professionals.add(professional);
      }
    });

    return professionals;
  }

  @Override
  @Transactional
  public void startOrder(UUID id, Long professionalId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));
    order.setStatus(Status.IN_PROGRESS);
    orderRepository.save(order);
  }

  @Override
  @Transactional
  public void completeOrder(UUID id, Long professionalId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));
    order.setStatus(Status.DONE);
    orderRepository.save(order);

    // Award 10 points to the customer for completing a service
    try {
      if (order.getCustomer() != null) {
        Points pts = Points.builder()
            .user(order.getCustomer())
            .amount(10)
            .reason("Serviço concluído")
            .orderId(id)
            .build();
        pointsRepository.save(pts);
      }
    } catch (Exception e) {
      log.warn("Não foi possível atribuir pontos ao cliente: {}", e.getMessage());
    }
  }

  @Override
  public java.util.List<Order> nearbyOrders(double lat, double lng, double radiusKm, String specialties) {
    Iterable<Order> all = orderRepository.findAll();
    java.util.List<Order> result = new java.util.ArrayList<>();
    String[] keywords = (specialties != null && !specialties.isBlank())
        ? specialties.toLowerCase().split("[,;\\s]+") : new String[0];
    for (Order o : all) {
      if (o.getStatus() != Status.OPEN) continue;
      if (o.getCustomer() == null) continue;
      Double oLat = o.getCustomer().getLatitude();
      Double oLng = o.getCustomer().getLongitude();
      if (oLat == null || oLng == null) continue;
      double dist = haversine(lat, lng, oLat, oLng);
      if (dist > radiusKm) continue;
      if (keywords.length > 0) {
        String productName = o.getProduct() != null ? (o.getProduct().getName() != null ? o.getProduct().getName().toLowerCase() : "") : "";
        String categoryName = (o.getProduct() != null && o.getProduct().getCategory() != null && o.getProduct().getCategory().getName() != null)
            ? o.getProduct().getCategory().getName().toLowerCase() : "";
        boolean matches = false;
        for (String kw : keywords) {
          if (!kw.isBlank() && (productName.contains(kw) || categoryName.contains(kw))) { matches = true; break; }
        }
        if (!matches) continue;
      }
      result.add(o);
    }
    return result;
  }

  private double haversine(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
