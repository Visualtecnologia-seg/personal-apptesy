package br.com.personalfighters.service;

public interface EmailService {
  void sendNewPassword(String email, String password, String name);
}
