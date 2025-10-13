package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;
import br.com.personalfighters.model.Notification;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserRecordsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

  private final OrderRepository orderRepository;
  private final UserRecordsService userRecordsService;

  @Override
  public void sendNotificationToNonNotifiedProfessionals() {
    try {
      Iterable<User> professionals = orderRepository.findNonNotifiedProfessionals(false);
      Iterable<Order> orders = orderRepository.findByHasSent(false);
      if (!ObjectUtils.isEmpty(orders) && !ObjectUtils.isEmpty(professionals)) {
        JSONArray notifications = createMessage(professionals);
        sendToExpo(notifications);
        orders.forEach(order -> order.setHasSent(true));
        orderRepository.saveAll(orders);
      }
    } catch (Exception e) {
      log.error("Failed to send push notification to non notified professionals.");
    }
  }

  @Override
  public void sendNotificationToProfessionalsOnOrder(Order order) {
    try {
      JSONArray notifications = createMessage(order.getAvailable());
      sendToExpo(notifications);
      order.setHasSent(true);
      orderRepository.save(order);
    } catch (Exception e) {
      log.error("Failed to send push notification to professionals in order.");
    }
  }

  @Override
  public void sendConfirmationToCustomer(User customer, User professional) {
    try {
      UserRecords customerRecords = userRecordsService.findByUser(customer);
      if (!ObjectUtils.isEmpty(customerRecords.getCustomerExpoPushNotificationToken())) {
        String msg = customer.getName() + ", o professor " + professional.getName() + " respondeu o seu pedido!";
        JSONArray notifications = new JSONArray();
        notificationToUser(notifications, msg, customerRecords.getCustomerExpoPushNotificationToken());
        sendToExpo(notifications);
      }
    } catch (Exception e) {
      log.error("Failed to send confirmation push notification to customer.");
    }
  }

  @Override
  public void sendConfirmationToProfessional(User customer, User professional, Order order) {
    try {
      UserRecords professionalRecords = userRecordsService.findByUser(professional);
      if (!ObjectUtils.isEmpty(professionalRecords.getProfessionalExpoPushNotificationToken())) {
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy");
        String msg = "A aula do dia " + df.format(order.getDate()) + " às " + order.getStartTime() + " de "
            + order.getProduct().getName() + " foi confirmada pelo aluno " + order.getCustomer().getName();
        JSONArray notifications = new JSONArray();
        notificationToUser(notifications, msg, professionalRecords.getProfessionalExpoPushNotificationToken());
        sendToExpo(notifications);
      }
    } catch (Exception e) {
      log.error("Failed to send push notification to professional.");
    }
  }

  @Override
  public void sendCancellationToCustomerAndProfessional(Order order) {
    try {
      UserRecords customerRecords = userRecordsService.findByUser(order.getCustomer());
      UserRecords professionalRecords = userRecordsService.findByUser(order.getProfessional());

      JSONArray customerNotification = new JSONArray();
      DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy");
      String msg = "A aula do dia " + df.format(order.getDate()) + " às " + order.getStartTime() + " de " + order.getProduct().getName() + " foi cancelada!";
      if (!ObjectUtils.isEmpty(customerRecords) && !ObjectUtils.isEmpty(customerRecords.getCustomerExpoPushNotificationToken())) {
        notificationToUser(customerNotification, msg, customerRecords.getCustomerExpoPushNotificationToken());
        sendToExpo(customerNotification);
      }
      JSONArray profesionalNotification = new JSONArray();
      if (!ObjectUtils.isEmpty(professionalRecords) && !ObjectUtils.isEmpty(professionalRecords.getProfessionalExpoPushNotificationToken())) {
        notificationToUser(profesionalNotification, msg, professionalRecords.getProfessionalExpoPushNotificationToken());
        sendToExpo(profesionalNotification);
      }
    } catch (Exception e) {
      log.error("Failed to send cancellation push notification to customer and professional.");
    }
  }

  @Override
  @Transactional(readOnly = true)
  public void sendChatNotification(Notification notification) {
    UserRecords to = userRecordsService.findByUser(notification.getTo());
    if (!ObjectUtils.isEmpty(to)) {
      JSONObject push = new JSONObject();
      String pushToken = notification.getFromRole().equals(Role.CUSTOMER) ? to.getProfessionalExpoPushNotificationToken() : to.getCustomerExpoPushNotificationToken();
      push.put("to", pushToken);
      push.put("sound", "default");
      push.put("title", "Personal Fighters");
      push.put("body", notification.getFrom().getName() + ": " + notification.getBody());
      push.put("data", notification.getData());
      JSONArray notifications = new JSONArray();
      notifications.put(push);
      sendToExpo(notifications);
    }
  }

  private void notificationToUser(JSONArray notifications, String text, String token) {
    notification(notifications, text, token);
  }

  private JSONArray createMessage(Iterable<User> professionals) {
    JSONArray notifications = new JSONArray();
    professionals.forEach(professional -> {
      UserRecords professionalRecords = userRecordsService.findByUser(professional);
      if (!ObjectUtils.isEmpty(professionalRecords) && !ObjectUtils.isEmpty(professionalRecords.getProfessionalExpoPushNotificationToken())) {
        String text = professional.getName() + ", há agendamentos em aberto esperando sua aprovação!";
        notificationToUser(notifications, text, professionalRecords.getProfessionalExpoPushNotificationToken());
      }
    });
    return notifications;
  }

  private void notification(JSONArray notifications, String msg, String token) {
    JSONObject notification = new JSONObject();
    notification.put("to", token);
    notification.put("sound", "default");
    notification.put("title", "Atenção");
    notification.put("body", msg);
    notifications.put(notification);
  }

  private void sendToExpo(JSONArray notifications) {
    String expoPushNotificationUrl = "https://exp.host/--/api/v2/push/send";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<String> request = new HttpEntity<>(notifications.toString(), headers);

    RestTemplate restTemplate = new RestTemplate();
    restTemplate.exchange(expoPushNotificationUrl, HttpMethod.POST, request, String.class);
  }

}
