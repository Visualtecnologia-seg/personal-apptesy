package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.QRToken;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.QRTokenRepository;
import br.com.personalfighters.service.QRTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QRTokenServiceImpl implements QRTokenService {

  private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 6;
  private static final int EXPIRY_MINUTES = 10;

  private final QRTokenRepository qrTokenRepository;
  private final OrderRepository orderRepository;

  @Override
  @Transactional
  public String generateArrivalCode(UUID orderId) {
    return generate(orderId, "ARRIVAL");
  }

  @Override
  @Transactional
  public String generateCompletionCode(UUID orderId) {
    return generate(orderId, "COMPLETION");
  }

  private String generate(UUID orderId, String type) {
    qrTokenRepository.findByOrderIdAndTypeAndUsedFalse(orderId, type)
        .ifPresent(t -> { t.setUsed(true); qrTokenRepository.save(t); });

    String code;
    do {
      code = randomCode();
    } while (qrTokenRepository.findByCode(code).isPresent());

    QRToken token = QRToken.builder()
        .orderId(orderId)
        .code(code)
        .type(type)
        .used(false)
        .expiresAt(LocalDateTime.now().plusMinutes(EXPIRY_MINUTES))
        .build();
    qrTokenRepository.save(token);
    return code;
  }

  @Override
  @Transactional
  public boolean validateAndUse(String code, UUID orderId, String type) {
    // 5 códigos errados no mesmo pedido: o código atual é descartado (impede adivinhar)
    String key = orderId + ":" + type;
    if (FAILS.getOrDefault(key, 0) >= 5) {
      qrTokenRepository.findByOrderIdAndTypeAndUsedFalse(orderId, type).ifPresent(t -> { t.setUsed(true); qrTokenRepository.save(t); });
      FAILS.remove(key);
      return false;
    }
    boolean ok = check(code == null ? "" : code, orderId, type);
    if (ok) FAILS.remove(key); else FAILS.merge(key, 1, Integer::sum);
    return ok;
  }

  private static final java.util.Map<String, Integer> FAILS = new java.util.concurrent.ConcurrentHashMap<>();

  private boolean check(String code, UUID orderId, String type) {
    return qrTokenRepository.findByCode(code.toUpperCase().trim())
        .filter(t -> !t.isUsed())
        .filter(t -> t.getOrderId().equals(orderId))
        .filter(t -> t.getType().equalsIgnoreCase(type))
        .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
        .map(t -> { t.setUsed(true); qrTokenRepository.save(t); return true; })
        .orElse(false);
  }

  private String randomCode() {
    java.security.SecureRandom rnd = new java.security.SecureRandom();
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
    return sb.toString();
  }

  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void autoConfirmCompletions() {
    // A liberação automática agora é só a do CompletionService (2h, respeitando disputa e SOS).
    // Este job antigo marcava "Concluído" depois de 24h mesmo com problema relatado — removido.
    qrTokenRepository.deleteExpired(LocalDateTime.now());
  }
}
