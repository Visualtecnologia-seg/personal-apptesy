package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.service.MessageService;
import br.com.personalfighters.service.impl.exceptions.MessageBlockedException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Messages"})
@RequestMapping("/messages")
public class MessageController {

  private final MessageService messageService;
  private final br.com.personalfighters.service.UserService userService;
  private final br.com.personalfighters.repository.OrderRepository orderRepository;

  // Só o cliente e o profissional do pedido (ou o administrador) leem e escrevem nesse chat.
  private static org.springframework.web.server.ResponseStatusException denied() {
    return new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso não permitido.");
  }
  /** Devolve "CUSTOMER", "PROFESSIONAL" ou "ADMIN" para quem está logado neste pedido. */
  private String roleIn(UUID orderId) {
    if (br.com.personalfighters.util.Redactor.isAdmin()) return "ADMIN";
    var me = userService.findAuthUser();
    var o = orderRepository.findById(orderId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
    if (o.getCustomer() != null && me.getId().equals(o.getCustomer().getId())) return "CUSTOMER";
    if (o.getProfessional() != null && me.getId().equals(o.getProfessional().getId())) return "PROFESSIONAL";
    throw denied();
  }
  private String roleInMessage(UUID messageId) {
    return roleIn(messageService.findById(messageId).getOrderId());
  }
  private void need(String role, String who) { if (!"ADMIN".equals(role) && !who.equals(role)) throw denied(); }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Lista mensagens de um pedido.")
  public List<Message> findByOrderId(@RequestParam UUID orderId) {
    roleIn(orderId);
    return messageService.findByOrderId(orderId);
  }

  @PostMapping
  @ApiOperation(value = "Envia uma mensagem (texto, imagem ou orçamento).")
  public ResponseEntity<?> send(@RequestBody Message message) {
    String role = roleIn(message.getOrderId());
    if (!"ADMIN".equals(role)) {
      message.setSenderId(userService.findAuthUser().getId()); // ninguém escreve em nome de outro
      message.setSenderRole(role);
      if ("QUOTE".equals(message.getMessageType()) && !"PROFESSIONAL".equals(role)) throw denied(); // orçamento só o profissional manda
    }
    if ("QUOTE".equals(message.getMessageType())) normalizeQuote(message);
    try {
      return ResponseEntity.ok(messageService.send(message));
    } catch (MessageBlockedException e) {
      return ResponseEntity.status(422).body(Map.of(
          "blocked", true,
          "attemptNumber", e.getAttemptNumber(),
          "matched", e.getMatched(),
          "errorMessage", e.getMessage()
      ));
    }
  }

  /**
   * Orçamento sempre nasce "aguardando o cliente" e com a conta feita pelo servidor:
   * por hora = valor/hora × horas; pelo serviço = valor fechado. Total = mão de obra + materiais.
   */
  private void normalizeQuote(Message m) {
    m.setQuoteStatus("PENDING");
    m.setCounterOfferAmount(null);
    m.setCounterOfferNote(null);
    m.setCounterOfferStatus(null);
    String mode = "SERVICE".equalsIgnoreCase(String.valueOf(m.getQuotePricing())) ? "SERVICE" : "HOUR";
    m.setQuotePricing(mode);
    BigDecimal max = new BigDecimal("100000");
    BigDecimal service;
    if ("HOUR".equals(mode) && m.getQuoteRate() != null && m.getQuoteHours() != null) {
      int h = Math.max(1, Math.min(m.getQuoteHours(), 24));
      m.setQuoteHours(h);
      service = m.getQuoteRate().multiply(BigDecimal.valueOf(h));
    } else {
      if ("SERVICE".equals(mode)) m.setQuoteRate(null);
      service = m.getQuoteServiceCost() != null ? m.getQuoteServiceCost() : m.getQuoteAmount();
    }
    if (service == null || service.signum() <= 0 || service.compareTo(max) > 0) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um valor válido para o serviço.");
    }
    BigDecimal materials = m.getQuoteMaterialsCost() == null ? BigDecimal.ZERO : m.getQuoteMaterialsCost().max(BigDecimal.ZERO).min(max);
    service = service.setScale(2, java.math.RoundingMode.HALF_UP);
    m.setQuoteServiceCost(service);
    m.setQuoteMaterialsCost(materials);
    m.setQuoteAmount(service);
    m.setQuoteTotalCost(service.add(materials));
  }

  @PutMapping("/{id}/quote-response")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Aceita ou recusa um orçamento (ACCEPTED | REJECTED).")
  public Message respondToQuote(@PathVariable UUID id,
                                @RequestBody Map<String, String> body) {
    need(roleInMessage(id), "CUSTOMER");
    String status = body.getOrDefault("status", "REJECTED");
    return messageService.respondToQuote(id, status);
  }

  @PutMapping("/{id}/counter-offer")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cliente faz contraproposta de valor.")
  public Message counterOffer(@PathVariable UUID id,
                              @RequestBody Map<String, Object> body) {
    need(roleInMessage(id), "CUSTOMER");
    BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0").toString());
    String note = body.getOrDefault("note", "").toString();
    return messageService.counterOffer(id, amount, note);
  }

  @PutMapping("/{id}/respond-counter")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Profissional aceita ou recusa contraproposta (ACCEPTED | REJECTED).")
  public Message respondToCounter(@PathVariable UUID id,
                                  @RequestBody Map<String, String> body) {
    need(roleInMessage(id), "PROFESSIONAL");
    String status = body.getOrDefault("status", "REJECTED");
    return messageService.respondToCounter(id, status);
  }

  @PutMapping("/{id}/check-item")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cliente marca/desmarca item da lista de compras.")
  public Message checkItem(@PathVariable UUID id,
                           @RequestBody Map<String, Object> body) {
    roleInMessage(id);
    String itemId = body.getOrDefault("itemId", "").toString();
    boolean checked = Boolean.parseBoolean(body.getOrDefault("checked", "true").toString());
    return messageService.checkItem(id, itemId, checked);
  }
}
