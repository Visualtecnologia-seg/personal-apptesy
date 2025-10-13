package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.Gender;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceImplTest {

  @Autowired
  OrderService orderService;
  @Autowired
  UserService userService;
  @Autowired
  AddressService addressService;
  @Autowired
  CreditCardService creditCardService;
  @Autowired
  ProductService productService;

  @Test
  void shouldSaveAndCancelOrder() {
    Order order = createOrder();
    orderService.save(order);
    assertNotNull(order);
    orderService.cancelOrder(order, Role.CUSTOMER);
  }

  @Test
  void shouldConfirmOrderByProfessional() {
    Order order = createOrder();
    List<User> professionals = orderService.findAvailable(order);
    orderService.confirmByProfessional(order.getId(), professionals.get(0).getId());
  }

  private Order createOrder() {
    User user = userService.findById(1L);
    Order order = new Order();
    order.setStatus(Status.OPEN);
    Page<Address> address = addressService.findAllByUser(user.getId(), null);

    order.setDate(LocalDate.now());
    order.setStartTime(LocalTime.parse("07:00"));
    order.setEndTime(LocalTime.parse("08:00"));
    order.setAddress(address.getContent().get(0));
    order.setCustomer(user);
    order.setNumberOfCustomers(1);
    order.setTotalCost(new BigDecimal(70));
    order.setResponse(new ArrayList<>());
    order.setAvailable(new ArrayList<>());
    order.setAddress(address.getContent().get(0));
    order.setGender(Gender.ANY);

    Product product = productService.findById(1L);
    order.setProduct(product);

    Payment payment = new Payment();
    Page<CreditCard> creditCards = creditCardService.findAllByUser(user.getId(), null);
    payment.setCard(creditCards.getContent().get(0));
    payment.setCardValue(new BigDecimal("70.0"));
    payment.setPaymentType(PaymentType.CREDIT_CARD);
    order.setPayment(payment);

    return  orderService.save(order);
  }

}