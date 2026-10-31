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
    return qrTokenRepository.findByCode(code.toUpperCase().trim())
        .filter(t -> !t.isUsed())
        .filter(t -> t.getOrderId().equals(orderId))
        .filter(t -> t.getType().equalsIgnoreCase(type))
        .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()))
        .map(t -> { t.setUsed(true); qrTokenRepository.save(t); return true; })
        .orElse(false);
  }

  private String randomCode() {
    Random rnd = new Random();
    StringBuilder sb = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
    return sb.toString();
  }

  @Scheduled(fixedRate = 3_600_000)
  @Transactional
  public void autoConfirmCompletions() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    List<Order> pending = orderRepository.findByStatus(Status.PENDING_CLIENT_CONFIRMATION);
    for (Order o : pending) {
      if (o.getRequestedCompletionAt() != null && o.getRequestedCompletionAt().isBefore(cutoff)) {
        o.setStatus(Status.DONE);
        if (o.getDoneAt() == null) o.setDoneAt(java.time.LocalDateTime.now());
        orderRepository.save(o);
        log.info("Auto-confirmação de conclusão do pedido {} após 24h sem resposta", o.getId());
      }
    }
    qrTokenRepository.deleteExpired(LocalDateTime.now());
  }
}
