package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface OrderService {

  Order findById(UUID id);

  Page<Order> findAll(Order order, Pageable pageable);

  Order save(Order order);

  void confirmByProfessional(UUID id, Long professionalId);

  void confirmByCustomer(UUID uuid, Long id);

  void rejectByProfessional(UUID id, Long professionalId);

  void cancelOrder(Order order, Role role);

  Order update(Order order);

  Page<Order> findByCustomer(Long id, Pageable pageable);

  Page<Order> findByCustomerAndStatus(Long id, List<Status> status, Pageable pageable);

  Page<Order> findByProfessional(Long id, Pageable pageable);

  Page<Order> findByProfessionalAndStatus(Long id, List<Status> status, Pageable pageable);

  Iterable<User> findNonNotifiedProfessionals(boolean hasSent);

  List<User> findAvailable(Order order);

  Iterable<Order> findByHasSent(boolean hasSent);

  void cancelOrdersByTimeExpiration();

  void cancelOrdersByMinimumTimeRequired();

  void setReviewed(UUID id);

  void setOrdersToDone();
}
