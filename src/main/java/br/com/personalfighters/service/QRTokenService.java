package br.com.personalfighters.service;

import java.util.UUID;

public interface QRTokenService {
  String generateArrivalCode(UUID orderId);
  String generateCompletionCode(UUID orderId);
  boolean validateAndUse(String code, UUID orderId, String type);
}
