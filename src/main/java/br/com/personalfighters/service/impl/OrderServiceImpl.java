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
  private final br.com.personalfighters.service.PointsAwarder pointsAwarder;
  private final br.com.personalfighters.service.TripService tripService;
  private final br.com.personalfighters.service.AsaasService asaasService;

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
    // O app manda só o id do cliente e do profissional: carrega as contas de verdade
    // (antes o pedido feito em "Agendar" quebrava no servidor ao procurar o perfil profissional).
    if (order.getProfessional() != null && order.getProfessional().getId() != null && order.getProfessional().getProfessional() == null) {
      order.setProfessional(userService.findById(order.getProfessional().getId()));
    }
    if (order.getCustomer() != null && order.getCustomer().getId() != null && order.getCustomer().getUsername() == null) {
      order.setCustomer(userService.findById(order.getCustomer().getId()));
    }
    if (order.getProduct() != null && order.getProduct().getId() != null) {
      productRepository.findById(order.getProduct().getId()).ifPresent(order::setProduct);
    }
    // Agendamento direto (sem orçamento) é de 1 hora quando o app não informa: assim o pedido já tem preço para pagar.
    boolean quote = order.getProduct() != null && Boolean.TRUE.equals(order.getProduct().getRequiresQuote());
    if (order.getHours() == null && !quote) order.setHours(1);
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
    applyPricing(order);
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
    if (!Boolean.TRUE.equals(professional.getVerified()) && !br.com.personalfighters.util.Redactor.isAdmin()) {
      throw new IllegalStateException("Verifique sua identidade (documento e selfie) para começar a receber chamados.");
    }
    Order order = orderRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));

    if (order.getCustomer() != null && order.getCustomer().getId().equals(professional.getId())) {
      throw new IllegalStateException("Você não pode atender um pedido feito por você mesmo.");
    }
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
    if (order.getCustomer() != null && order.getCustomer().getId().equals(professional.getId())) {
      throw new IllegalStateException("Você não pode atender um pedido feito por você mesmo.");
    }

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
    Status st0 = order.getStatus();
    if (st0 == Status.CANCELLED) return;
    // Cliente e profissional só cancelam ANTES do atendimento começar. Depois disso, é pelo "Tive um problema" (a equipe decide).
    if (!role.equals(Role.ADMIN) && !(st0 == Status.OPEN || st0 == Status.BLOCKED
        || st0 == Status.CONFIRMED || st0 == Status.AWAITING_PAYMENT)) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
          "O atendimento já começou e não pode mais ser cancelado. Se houve algum problema, use \"Tive um problema\" no pedido.");
    }
    order.setAvailable(new ArrayList<>());
    order.setResponse(new ArrayList<>());
    BigDecimal keep = BigDecimal.ZERO;

    if (role.equals(Role.CUSTOMER) && order.getProfessional() != null
        && (order.getStatus().equals(Status.CONFIRMED) || order.getStatus().equals(Status.AWAITING_PAYMENT))) {
      try {
        BigDecimal fee = tripService.applyCancelFee(order);
        if (fee.signum() > 0) {
          if (Boolean.TRUE.equals(order.getPixPaid())) {
            // a taxa sai do valor pago pelo cliente (o resto é devolvido)
            if (asaasService.transferToProfessional(order.getProfessional(), fee, "Taxa de deslocamento #" + order.getId().toString().substring(0, 8))) keep = fee;
          }
          notificationService.sendToUser(order.getProfessional(), true,
              "O cliente cancelou depois que você saiu. Você recebe R$ " + fee.setScale(2) + " pelo deslocamento.");
        }
      } catch (Exception e) {
        log.warn("Taxa de cancelamento não aplicada: {}", e.getMessage());
      }
    }
    boolean paidNotReleased = Boolean.TRUE.equals(order.getPixPaid()) && !Boolean.TRUE.equals(order.getPixReleased());
    if (order.getStatus().equals(Status.CONFIRMED) || order.getStatus().equals(Status.AWAITING_PAYMENT) || order.getStatus().equals(Status.OPEN) || paidNotReleased) {
      try {
        transactionService.refundPayment(order, keep);
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
    // Antes: pedido aceito e NÃO pago virava "Concluído" sozinho depois do horário (sem serviço e sem pagamento).
    // Agora: se o horário passou e o cliente não pagou, o pedido é cancelado e os dois são avisados.
    orders.forEach(order -> {
      if (Boolean.TRUE.equals(order.getPixPaid())) return;
      try {
        order.setCancelledBy("SYSTEM");
        order.setCancelReason("O horário passou e o pagamento não foi feito");
        orderRepository.save(order);
        cancelOrder(order, Role.ADMIN);
      } catch (Exception e) {
        log.warn("Não foi possível cancelar o pedido {} sem pagamento: {}", order.getId(), e.getMessage());
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

    list = list.stream().filter(user -> user != null && user.getProfessional() != null && !Boolean.FALSE.equals(user.getProfessional().getActive())).collect(Collectors.toList());

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

  /** Taxa de 5% embutida: total do cliente = horas × valor/hora × 1,05; o profissional recebe o valor cheio. */
  private void applyPricing(Order order) {
    try {
      if (order.getServiceValue() == null && order.getHours() != null && order.getProfessional() != null
          && order.getProfessional().getId() != null) {
        User pro = userService.findById(order.getProfessional().getId());
        Double rate = pro != null && pro.getProfessional() != null ? pro.getProfessional().getHourlyRate() : null;
        if (rate != null && rate > 0) {
          java.math.BigDecimal service = java.math.BigDecimal.valueOf(rate)
              .multiply(java.math.BigDecimal.valueOf(order.getHours().doubleValue()))
              .setScale(2, java.math.RoundingMode.HALF_UP);
          order.setServiceValue(service);
        }
      }
      if (order.getServiceValue() == null && order.getTotalCost() != null) {
        order.setServiceValue(order.getTotalCost());
      }
      if (order.getServiceValue() != null) {
        order.setTotalCost(br.com.personalfighters.util.Pricing.clientTotal(order.getServiceValue()));
      }
    } catch (Exception e) {
      log.warn("Não foi possível calcular o preço do pedido: {}", e.getMessage());
    }
  }

  @Override
  @Transactional
  public void completeOrder(UUID id, Long professionalId) {
    Order order = orderRepository.findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Pedido não encontrado"));
    order.setStatus(Status.DONE);
    if (order.getDoneAt() == null) order.setDoneAt(LocalDateTime.now(zoneId));
    orderRepository.save(order);

    pointsAwarder.award(order);
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
