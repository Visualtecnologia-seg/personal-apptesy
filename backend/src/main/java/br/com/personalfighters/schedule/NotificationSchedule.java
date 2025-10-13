package br.com.personalfighters.schedule;

import br.com.personalfighters.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationSchedule {

  private static final String TIME_ZONE = "America/Sao_Paulo";
  private final NotificationService notificationService;

  @Scheduled(cron = "0 0 0/5 * * *", zone = TIME_ZONE)
  @Scheduled(fixedRate = 1000 * 60 * 5)
  public void sendNotificationToNonNotifiedProfessionals() {
    notificationService.sendNotificationToNonNotifiedProfessionals();
  }

}
