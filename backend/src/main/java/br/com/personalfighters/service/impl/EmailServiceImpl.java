package br.com.personalfighters.service.impl;

import br.com.personalfighters.service.EmailService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@AllArgsConstructor
@Service
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender emailSender;

  @Override
  public void sendNewPassword(String email, String password, String name) {
    String mailText = "Olá " + name + ", sua senha provisória é: " + password;
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Personal Fighter - Nova senha provisória!");
    message.setText(mailText);
    try {
      emailSender.send(message);
    } catch (MailException e) {
      log.error("Failed to send email", e);
    }
  }
}
