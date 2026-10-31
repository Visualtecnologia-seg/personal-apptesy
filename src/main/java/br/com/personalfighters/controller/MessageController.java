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

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Lista mensagens de um pedido.")
  public List<Message> findByOrderId(@RequestParam UUID orderId) {
    return messageService.findByOrderId(orderId);
  }

  @PostMapping
  @ApiOperation(value = "Envia uma mensagem (texto, imagem ou orçamento).")
  public ResponseEntity<?> send(@RequestBody Message message) {
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

  @PutMapping("/{id}/quote-response")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Aceita ou recusa um orçamento (ACCEPTED | REJECTED).")
  public Message respondToQuote(@PathVariable UUID id,
                                @RequestBody Map<String, String> body) {
    String status = body.getOrDefault("status", "REJECTED");
    return messageService.respondToQuote(id, status);
  }

  @PutMapping("/{id}/counter-offer")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cliente faz contraproposta de valor.")
  public Message counterOffer(@PathVariable UUID id,
                              @RequestBody Map<String, Object> body) {
    BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0").toString());
    String note = body.getOrDefault("note", "").toString();
    return messageService.counterOffer(id, amount, note);
  }

  @PutMapping("/{id}/respond-counter")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Profissional aceita ou recusa contraproposta (ACCEPTED | REJECTED).")
  public Message respondToCounter(@PathVariable UUID id,
                                  @RequestBody Map<String, String> body) {
    String status = body.getOrDefault("status", "REJECTED");
    return messageService.respondToCounter(id, status);
  }

  @PutMapping("/{id}/check-item")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cliente marca/desmarca item da lista de compras.")
  public Message checkItem(@PathVariable UUID id,
                           @RequestBody Map<String, Object> body) {
    String itemId = body.getOrDefault("itemId", "").toString();
    boolean checked = Boolean.parseBoolean(body.getOrDefault("checked", "true").toString());
    return messageService.checkItem(id, itemId, checked);
  }
}
