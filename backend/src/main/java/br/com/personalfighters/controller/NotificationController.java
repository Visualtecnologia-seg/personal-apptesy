package br.com.personalfighters.controller;

import br.com.personalfighters.model.Notification;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.NotificationService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


@CrossOrigin
@RestController
@RequiredArgsConstructor
@Slf4j
@Api(tags = {"Notifications"})
@RequestMapping("/notifications")
public class NotificationController {

  /* Repositories */
  private final UserRepository userRepository;
  /* Services */
  private final NotificationService notificationService;

  @PostMapping
  public void sendNotification(@RequestBody Notification notification) {
    notificationService.sendChatNotification(notification);
  }

}
