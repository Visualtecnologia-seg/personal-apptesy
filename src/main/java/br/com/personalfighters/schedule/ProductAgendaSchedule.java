package br.com.personalfighters.schedule;

import br.com.personalfighters.service.ProductAgendaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductAgendaSchedule {

  private final ProductAgendaService productAgendaService;

  @Scheduled(cron = "0 0/1 * * * *")
  public void updateAllProductAgendas() {
    productAgendaService.updateAllProductAgendas();
  }
}
