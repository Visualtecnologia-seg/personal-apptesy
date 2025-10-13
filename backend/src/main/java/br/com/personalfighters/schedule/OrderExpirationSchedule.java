package br.com.personalfighters.schedule;

import br.com.personalfighters.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExpirationSchedule {

  private final OrderService orderService;

  @Scheduled(cron = "0 0/30 * * * *")
  public void expiresOrdersByTime() {
    log.info("Cancelando as ordens com tempo expirado");
    orderService.cancelOrdersByTimeExpiration();

    log.info("Cancelando as ordens não confirmadas e abaixo do tempo mínimo requerido");
    orderService.cancelOrdersByMinimumTimeRequired();
  }

  @Scheduled(cron = "0 0/30 * * * *")
  public void doneOrders() {
    log.info("Alterando as ordens confirmadas para realizadas após o término da aula");
    orderService.setOrdersToDone();
  }
}
