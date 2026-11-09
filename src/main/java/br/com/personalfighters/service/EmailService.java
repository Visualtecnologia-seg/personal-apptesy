package br.com.personalfighters.service;

public interface EmailService {
  void sendNewPassword(String email, String password, String name);
  void sendWelcome(String email, String name);
  void sendOrderConfirmed(String email, String name, String date, String time);
  void sendOrderCancelled(String email, String name);
  void sendPayoutCode(String email, String name, String code);
  void sendResetCode(String email, String name, String code);

  /** E-mail simples (códigos e avisos de segurança). */
  void sendText(String to, String subject, String text);

  /** Falso quando o servidor ainda não tem o e-mail configurado (MAIL_USERNAME/MAIL_PASSWORD). */
  boolean isConfigured();
}
