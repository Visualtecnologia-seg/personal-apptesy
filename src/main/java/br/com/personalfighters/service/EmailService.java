package br.com.personalfighters.service;

import br.com.personalfighters.util.MailTemplate;

public interface EmailService {
  void sendNewPassword(String email, String password, String name);
  void sendWelcome(String email, String name);
  /** Boas-vindas: versão do profissional (pro = true) ou do cliente. */
  void sendWelcome(String email, String name, boolean pro);
  void sendOrderConfirmed(String email, String name, String date, String time);
  void sendOrderCancelled(String email, String name);
  void sendPayoutCode(String email, String name, String code);
  void sendResetCode(String email, String name, String code);
  /** Código para confirmar o e-mail no cadastro. */
  void sendEmailCode(String email, String code);
  /** Aviso de segurança (novo acesso, celular trocado). */
  void sendSecurityAlert(String email, String name, String subject, String title, String text);

  /** E-mail simples (texto corrido) já com o visual da marca. */
  void sendText(String to, String subject, String text);

  /** E-mail montado com o MailTemplate. Enviado em segundo plano. */
  void send(String to, String subject, MailTemplate t);

  /** Falso quando o servidor ainda não tem o e-mail configurado (MAIL_USERNAME/MAIL_PASSWORD). */
  boolean isConfigured();
}
