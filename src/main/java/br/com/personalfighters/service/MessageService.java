package br.com.personalfighters.service;

import br.com.personalfighters.entity.Message;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface MessageService {
  List<Message> findByOrderId(UUID orderId);
  Message send(Message message);
  Message respondToQuote(UUID messageId, String quoteStatus);
  Message counterOffer(UUID messageId, BigDecimal amount, String note);
  Message respondToCounter(UUID messageId, String status);
  Message checkItem(UUID messageId, String itemId, boolean checked);
  Message findById(UUID id);
}
