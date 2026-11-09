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
    send(email, "Nova senha provisória",
        "Olá " + name + ",\n\nA sua senha provisória é: " + password +
            "\n\nAlterá-la após o primeiro acesso.");
  }

  @Override
  public void sendWelcome(String email, String name) {
    send(email, "Bem-vindo à plataforma!",
        "Olá " + name + ",\n\nA sua conta foi criada com sucesso! Já pode iniciar sessão " +
            "e explorar os serviços disponíveis.\n\nObrigado por se juntar a nós!");
  }

  @Override
  public void sendOrderConfirmed(String email, String name, String date, String time) {
    send(email, "Pedido confirmado!",
        "Olá " + name + ",\n\nO seu pedido foi confirmado para o dia " + date +
            " às " + time + ".\n\nFique atento à chegada do prestador no horário combinado.\n\nObrigado!");
  }

  @Override
  public void sendOrderCancelled(String email, String name) {
    send(email, "Pedido cancelado",
        "Olá " + name + ",\n\nInformamos que o seu pedido foi cancelado.\n\n" +
            "Se tiver dúvidas ou precisar de ajuda, entre em contacto com o suporte.\n\nObrigado!");
  }

  @Override
  public void sendPayoutCode(String email, String name, String code) {
    send(email, "Código de confirmação de saque — Profissional Opus",
        "Olá " + name + ",\n\n" +
            "Recebemos o seu pedido de saque.\n\n" +
            "O seu código de confirmação é:\n\n" +
            "       ► " + code + " ◄\n\n" +
            "Este código é válido por 15 minutos.\n" +
            "Se não solicitou um saque, ignore este e-mail e contacte o suporte.\n\n" +
            "Atenciosamente,\nEquipa Profissional Opus");
  }

  @Override
  public void sendResetCode(String email, String name, String code) {
    send(email, "Código para criar uma nova senha — OPUS",
        "Olá " + name + ",\n\nSeu código para criar uma nova senha é:\n\n       " + code +
            "\n\nEle vale por 15 minutos. Sua senha atual continua valendo até você criar a nova.\n" +
            "Se não foi você que pediu, ignore este e-mail.\n\nEquipe OPUS");
  }

  @Override
  public void sendText(String to, String subject, String text) {
    send(to, subject, text);
  }

  @Override
  public boolean isConfigured() {
    String u = System.getenv("MAIL_USERNAME"), p = System.getenv("MAIL_PASSWORD");
    return u != null && !u.isBlank() && p != null && !p.isBlank();
  }

  private void send(String to, String subject, String text) {
    SimpleMailMessage message = new SimpleMailMessage();
    // Remetente: MAIL_FROM (outro provedor de e-mail) ou a própria conta (Gmail)
    String from = System.getenv("MAIL_FROM");
    if (from == null || from.isBlank()) from = System.getenv("MAIL_USERNAME");
    if (from != null && from.contains("@")) message.setFrom("OPUS <" + from.trim() + ">");
    message.setTo(to);
    message.setSubject(subject);
    message.setText(text);
    try {
      emailSender.send(message);
    } catch (MailException e) {
      log.error("Falha ao enviar e-mail para {}: {}", to, e.getMessage());
    }
  }
}
