package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.BlockedAttempt;
import br.com.personalfighters.entity.Message;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.repository.BlockedAttemptRepository;
import br.com.personalfighters.repository.MessageRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.MessageService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.impl.exceptions.MessageBlockedException;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

  private final MessageRepository messageRepository;
  private final OrderRepository orderRepository;
  private final NotificationService notificationService;
  private final ObjectMapper objectMapper;
  private final BlockedAttemptRepository blockedAttemptRepository;

  // ── Contact-sharing filter ──────────────────────────────────────────────────

  private static final Pattern PHONE_PATTERN = Pattern.compile(
      "(\\(\\d{2}\\)[\\s-]?\\d{4,5}[\\s-]?\\d{4})" +
      "|(\\b\\d{2}[\\s.]?\\d{4,5}[\\s.-]?\\d{4}\\b)" +
      "|(\\b9\\d{8}\\b)"
      , Pattern.CASE_INSENSITIVE);

  private static final List<String> BLOCKED_KEYWORDS = List.of(
      "whatsapp", "wpp", "zap zap", "zap", "insta", "@",
      "manda seu número", "manda seu numero",
      "me passa seu número", "me passa seu numero",
      "me passa seu contato", "me passa seu whatsapp",
      "me passa seu zap", "me passa seu wpp",
      "fala comigo no", "me chama no",
      "meu número é", "meu numero é", "meu numero e",
      "meu zap é", "meu zap e",
      "meu contato é", "meu contato e",
      "meu whatsapp é", "meu whatsapp e",
      "passa seu número", "passa seu numero",
      "passa seu contato", "passa seu zap"
  );

  private String detectViolation(String text) {
    if (text == null) return null;
    String lower = text.toLowerCase().trim();

    for (String kw : BLOCKED_KEYWORDS) {
      if (lower.contains(kw)) return "keyword:" + kw;
    }
    if (PHONE_PATTERN.matcher(text).find()) return "phone_number";
    return null;
  }

  private String buildBlockedMessage(int attempt) {
    if (attempt == 1) {
      return "Mensagem não enviada — A sua mensagem contém um contacto externo. " +
          "Na Profissional Opus, todas as comunicações e pagamentos acontecem dentro da plataforma, " +
          "garantindo segurança e suporte para ambas as partes.";
    } else if (attempt == 2) {
      return "Mensagem não enviada — Esta é a sua segunda tentativa. " +
          "Continuamos a protegê-lo, mas pedimos que respeite as regras da plataforma. " +
          "Transações fora do app não têm cobertura nem suporte.";
    } else {
      return "Mensagem não enviada — Foram detetadas várias tentativas de partilha de contactos externos. " +
          "A sua conta está a ser monitorada. Reincidências podem resultar em suspensão.";
    }
  }

  // ── Service methods ─────────────────────────────────────────────────────────

  @Override
  public List<Message> findByOrderId(UUID orderId) {
    return messageRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
  }

  @Override
  public Message send(Message message) {
    if ("TEXT".equals(message.getMessageType()) && message.getText() != null) {
      String matched = detectViolation(message.getText());
      if (matched != null) {
        long prevAttempts = blockedAttemptRepository.countByOrderIdAndSenderId(
            message.getOrderId(), message.getSenderId());
        int attemptNumber = (int) prevAttempts + 1;

        blockedAttemptRepository.save(BlockedAttempt.builder()
            .orderId(message.getOrderId())
            .senderId(message.getSenderId())
            .senderRole(message.getSenderRole())
            .matchedPattern(matched)
            .originalText(message.getText())
            .attemptNumber(attemptNumber)
            .build());

        throw new MessageBlockedException(buildBlockedMessage(attemptNumber), attemptNumber, matched);
      }
    }
    return messageRepository.save(message);
  }

  @Override
  public Message respondToQuote(UUID messageId, String status) {
    Message msg = findById(messageId);
    if (!"QUOTE".equals(msg.getMessageType())) {
      throw new RuntimeException("Mensagem não é um orçamento.");
    }
    if ("ACCEPTED".equals(msg.getQuoteStatus()) || "REJECTED".equals(msg.getQuoteStatus())) {
      throw new RuntimeException("Este orçamento já foi respondido.");
    }

    msg.setQuoteStatus(status);

    if ("ACCEPTED".equals(status)) {
      BigDecimal total = msg.getQuoteTotalCost() != null
          ? msg.getQuoteTotalCost()
          : (msg.getQuoteAmount() != null ? msg.getQuoteAmount() : BigDecimal.ZERO);

      orderRepository.findById(msg.getOrderId()).ifPresent(order -> {
        order.setTotalCost(total);
        orderRepository.save(order);
        try {
          notificationService.sendPaymentConfirmedToProfessional(order);
        } catch (Exception e) {
          log.warn("Notificação de aceitação de orçamento não enviada: {}", e.getMessage());
        }
      });
    }

    return messageRepository.save(msg);
  }

  @Override
  public Message counterOffer(UUID messageId, BigDecimal amount, String note) {
    Message msg = findById(messageId);
    if (!"QUOTE".equals(msg.getMessageType())) {
      throw new RuntimeException("Mensagem não é um orçamento.");
    }
    if (!"PENDING".equals(msg.getQuoteStatus())) {
      throw new RuntimeException("Orçamento não está em estado PENDING para contraproposta.");
    }
    msg.setQuoteStatus("NEGOTIATING");
    msg.setCounterOfferAmount(amount);
    msg.setCounterOfferNote(note);
    msg.setCounterOfferStatus("PENDING_PRO");
    return messageRepository.save(msg);
  }

  @Override
  public Message respondToCounter(UUID messageId, String status) {
    Message msg = findById(messageId);
    if (!"NEGOTIATING".equals(msg.getQuoteStatus())) {
      throw new RuntimeException("Não há contraproposta pendente.");
    }
    msg.setCounterOfferStatus(status.equals("ACCEPTED") ? "ACCEPTED_BY_PRO" : "REJECTED_BY_PRO");

    if ("ACCEPTED".equals(status)) {
      msg.setQuoteStatus("ACCEPTED");
      BigDecimal total = msg.getCounterOfferAmount() != null
          ? msg.getCounterOfferAmount()
          : msg.getQuoteTotalCost();
      msg.setQuoteTotalCost(total);

      orderRepository.findById(msg.getOrderId()).ifPresent(order -> {
        order.setTotalCost(total);
        orderRepository.save(order);
        try {
          notificationService.sendPaymentLockedToCustomer(order);
        } catch (Exception e) {
          log.warn("Notificação de contraproposta aceita não enviada: {}", e.getMessage());
        }
      });
    } else {
      msg.setQuoteStatus("REJECTED");
    }

    return messageRepository.save(msg);
  }

  @Override
  public Message checkItem(UUID messageId, String itemId, boolean checked) {
    Message msg = findById(messageId);
    try {
      List<String> checkedIds = msg.getClientCheckedItems() != null
          ? objectMapper.readValue(msg.getClientCheckedItems(), new TypeReference<List<String>>() {})
          : new ArrayList<>();

      if (checked && !checkedIds.contains(itemId)) {
        checkedIds.add(itemId);
      } else if (!checked) {
        checkedIds.remove(itemId);
      }
      msg.setClientCheckedItems(objectMapper.writeValueAsString(checkedIds));
    } catch (Exception e) {
      log.error("Erro ao actualizar checklist: {}", e.getMessage());
    }
    return messageRepository.save(msg);
  }

  @Override
  public Message findById(UUID id) {
    return messageRepository.findById(id)
        .orElseThrow(() -> new ObjectNotFoundException("Mensagem não encontrada: " + id));
  }
}
