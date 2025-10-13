package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Notification;

public interface NotificationService {

  void sendNotificationToNonNotifiedProfessionals();

  void sendNotificationToProfessionalsOnOrder(Order order);

  void sendConfirmationToCustomer(User customer, User professional);

  void sendConfirmationToProfessional(User customer, User professional, Order order);

  void sendCancellationToCustomerAndProfessional(Order order);

  void sendChatNotification(Notification notification);
}
