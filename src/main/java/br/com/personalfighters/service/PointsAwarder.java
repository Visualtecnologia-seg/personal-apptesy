package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Points;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.PointsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Pontos por serviço concluído: 10 pontos, só uma vez por pedido,
 * e só em serviços a partir de R$ 20 (para a taxa cobrir a reserva de R$ 1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsAwarder {

  public static volatile int POINTS_PER_SERVICE = 10;
  public static volatile BigDecimal MIN_SERVICE_FOR_POINTS = new BigDecimal("20");

  private final PointsRepository pointsRepository;
  private final OrderRepository orderRepository;

  public void award(Order order) {
    try {
      if (order == null || order.getCustomer() == null) return;
      if (Boolean.TRUE.equals(order.getPointsAwarded())) return;
      BigDecimal service = order.getServiceValue() != null ? order.getServiceValue() : order.getTotalCost();
      if (service == null || service.compareTo(MIN_SERVICE_FOR_POINTS) < 0) return;
      pointsRepository.save(Points.builder()
          .user(order.getCustomer())
          .amount(POINTS_PER_SERVICE)
          .reason("Serviço concluído")
          .orderId(order.getId())
          .build());
      order.setPointsAwarded(true);
      orderRepository.save(order);
    } catch (Exception e) {
      log.warn("Não foi possível dar pontos ao cliente: {}", e.getMessage());
    }
  }
}
