package br.com.personalfighters.controller;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.ProductRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AddressService;
import br.com.personalfighters.service.CreditCardService;
import br.com.personalfighters.service.OrderService;
import br.com.personalfighters.utils.DataUtils;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@CrossOrigin
@RestController
@RequiredArgsConstructor
@Slf4j
@Api(tags = {"Customers"})
@RequestMapping("/system")
public class SystemController {

  /* Repositories */
  private final UserRepository userRepository;
  private final OrderRepository orderRepository;
  private final ProductRepository productRepository;
  /* Services */
  private final OrderService orderService;
  private final AddressService addressService;
  private final CreditCardService creditCardService;

  @GetMapping("/create")
  @ResponseStatus(HttpStatus.OK)
  public void create() {
    List<User> users = userRepository.findAll();
    List<Product> products = productRepository.findAll();
    users.forEach(customer -> createOpenOrder(customer, products));
  }

  private void createOpenOrder(User customer, List<Product> products) {
    Page<Address> address = addressService.findAllByUser(customer.getId(), null);
    Page<CreditCard> creditCards = creditCardService.findAllByUser(customer.getId(), null);

    log.info("Starting create orders for " + customer.getId());
    for (int i = 0; i < 50; i++) {
      Order order = new Order();
      order.setStatus(Status.OPEN);

      String[] times = {"07:00", "07:30", "08:00", "09:00", "09:30", "10:00", "11:00", "11:30", "12:00", "13:00",
          "14:00", "14:30", "15:00", "16:00", "16:30", "17:00", "18:00", "18:30", "19:00", "19:30", "20:00", "20:30", "21:00", "22:00"};

      LocalTime startTime = LocalTime.parse(times[new Random().nextInt(times.length)]);
      LocalDate date = LocalDate.now().plusDays(i);

      if (i < 30) {
        date = LocalDate.now().minusDays(i);
      }

      order.setDate(date);
      order.setStartTime(startTime);
      order.setEndTime(startTime.plusMinutes(60));
      order.setAddress(address.getContent().get(new Random().nextInt(address.getContent().size())));
      order.setCustomer(customer);
      order.setNumberOfCustomers(1);
      order.setTotalCost(new BigDecimal(70));
      order.setResponse(new ArrayList<>());
      order.setAvailable(new ArrayList<>());
      order.setAddress(address.getContent().get(0));
      order.setGender(Gender.ANY);

      int index = DataUtils.random(products.size());
      Product product = products.get(index);
      order.setProduct(product);

      Payment payment = new Payment();

      payment.setCard(creditCards.getContent().get(0));
      payment.setCardValue(new BigDecimal("70.0"));
      payment.setPaymentType(PaymentType.CREDIT_CARD);
      order.setPayment(payment);

      orderService.save(order);
    }
    log.info("Starting confirming orders for " + customer.getId());
    confirm(customer);
    log.info("Starting finishing orders for " + customer.getId());
    done(customer);
  }

  @GetMapping("/confirm")
  @ResponseStatus(HttpStatus.OK)
  public void confirm(User customer) {
    List<Order> orders = orderRepository.findAllByCustomer(customer);
    orders.forEach(order -> {
      List<User> available = orderService.findAvailable(order);
      int size = Math.min(available.size(), 3);
      for (int i = 0; i < size; i++) {
        orderService.confirmByProfessional(order.getId(), available.get(i).getId());
      }
    });
  }

  @GetMapping("/done")
  @ResponseStatus(HttpStatus.OK)
  public void done(User customer) {
    List<Order> orders = orderRepository.findAllByCustomer(customer);
    orders.forEach(order -> {
      List<User> response = order.getResponse();
      if (response.size() == 0) {
        return;
      }

      if (order.getDate().isAfter(LocalDate.now()) && order.getDate().isBefore(LocalDate.now().plusDays(50))) {
        if (response.size() > 0) {
          orderService.confirmByCustomer(order.getId(), response.get(0).getId());
        }
      }

      if (order.getDate().isAfter(LocalDate.now().minusDays(15)) && order.getDate().isBefore(LocalDate.now().minusDays(0))) {
        orderService.cancelOrder(order, Role.CUSTOMER);
      }

      if (order.getDate().isAfter(LocalDate.now().minusDays(30)) && order.getDate().isBefore(LocalDate.now().minusDays(15))) {
        orderService.confirmByCustomer(order.getId(), response.get(0).getId());
        order.setStatus(Status.DONE);
        orderService.update(order);
      }

    });
  }

}
