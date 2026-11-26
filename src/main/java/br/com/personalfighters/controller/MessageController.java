package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.service.MessageService;
import br.com.personalfighters.service.impl.exceptions.MessageBlockedException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Api(tags = {"Messages"})
@RequestMapping("/messages")
public class MessageController {

  private final MessageService messageService;
  private final br.com.personalfighters.service.UserService userService;
  private final br.com.personalfighters.repository.OrderRepository orderRepository;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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
  static final String SECURITY_NOTICE = "⚠️ Aviso de Segurança — Esta prestação de serviço está sendo gravada por áudio a partir deste momento, conforme os termos de uso da plataforma Profissional Opus. A gravação destina-se exclusivamente a fins de segurança e proteção de ambas as partes.";

  private void need(String role, String who) { if (!"ADMIN".equals(role) && !who.equals(role)) throw denied(); }

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Lista mensagens de um pedido.")
  public List<Message> findByOrderId(@RequestParam UUID orderId) {
    roleIn(orderId);
    // A lista é pedida a cada 4 segundos: as fotos (que ficam guardadas em texto) não vão nela.
    // No lugar vai um link fixo e assinado, que o celular baixa uma vez e guarda.
    List<Message> out = jdbc.query("SELECT * FROM (SELECT id, order_id, sender_id, sender_name, sender_role, text, " +
        "CASE WHEN image_url LIKE 'data:%' THEN NULL ELSE image_url END AS image_url, (image_url LIKE 'data:%') AS has_data, " +
        "message_type, quote_status, quote_amount, quote_description, quote_hours, quote_items_json, quote_service_cost, quote_materials_cost, " +
        "quote_total_cost, quote_pricing, quote_rate, counter_offer_amount, counter_offer_note, counter_offer_status, client_checked_items, created_at " +
        "FROM message_ WHERE order_id = ? ORDER BY created_at DESC LIMIT 1000) x ORDER BY created_at ASC", (rs, i) -> {
      Message m = ROW.mapRow(rs, i);
      UUID id = (UUID) rs.getObject("id");
      try { ID_FIELD.set(m, id); } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
      if (rs.getBoolean("has_data")) m.setImageUrl(br.com.personalfighters.util.MailTemplate.base() + "/chat/image/" + id + "?k=" + imageKey(id));
      return m;
    }, orderId);
    return out;
  }

  private static final org.springframework.jdbc.core.BeanPropertyRowMapper<Message> ROW = new org.springframework.jdbc.core.BeanPropertyRowMapper<>(Message.class);
  private static final java.lang.reflect.Field ID_FIELD;
  static {
    try { ID_FIELD = Message.class.getDeclaredField("id"); ID_FIELD.setAccessible(true); } catch (NoSuchFieldException e) { throw new ExceptionInInitializerError(e); }
  }

  @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
  private String jwtSecret;

  /** Assinatura fixa de cada foto do chat (só quem recebeu a lista do pedido tem o link). */
  String imageKey(UUID id) {
    try {
      javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(("chat-foto:" + br.com.personalfighters.util.AppSecret.effective(jwtSecret, jdbc)).getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 32);
    } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
  }

  @PostMapping
  @ApiOperation(value = "Envia uma mensagem (texto, imagem ou orçamento).")
  public ResponseEntity<?> send(@RequestBody Message message) {
    String role = roleIn(message.getOrderId());
    if (!"ADMIN".equals(role)) {
      message.setSenderId(userService.findAuthUser().getId()); // ninguém escreve em nome de outro
      message.setSenderRole(role);
      // Só o sistema escreve avisos "do OPUS"; usuário manda texto, foto e (pela outra rota) áudio
      String type = message.getMessageType() == null ? "TEXT" : message.getMessageType().trim().toUpperCase(java.util.Locale.ROOT);
      // orçamento só o profissional manda (a regra vem depois de padronizar o tipo: antes "quote" em minúsculas passava)
      if ("QUOTE".equals(type) && !"PROFESSIONAL".equals(role)) throw denied();
      if (!br.com.personalfighters.util.RateLimit.allow("chat:" + message.getOrderId() + ":" + role, 20, 60) || !br.com.personalfighters.util.RateLimit.allow("chatday:" + role + ":" + message.getOrderId(), 500, 86400))
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas mensagens seguidas. Espere um pouco para mandar mais.");
      br.com.personalfighters.entity.User me = userService.findAuthUser();
      if ("SYSTEM".equals(type) && jdbc.queryForObject("SELECT COUNT(*) FROM protection_session_ WHERE order_id = ? AND user_id = ? AND active = TRUE", Long.class, message.getOrderId(), me.getId()) > 0) {
        // Único aviso "do sistema" que o app manda: o de gravação de segurança, com o texto oficial (não dá para escrever outro)
        message.setText(SECURITY_NOTICE);
        message.setSenderName("Sistema de Segurança");
      } else {
        if (!java.util.Set.of("TEXT", "IMAGE", "QUOTE").contains(type)) type = "TEXT";
        String nm = ((me.getName() == null ? "" : me.getName()) + (me.getSurname() == null ? "" : " " + me.getSurname())).trim();
        message.setSenderName(nm.isEmpty() ? null : nm); // nome de quem escreveu (ninguém se passa pelo OPUS)
      }
      message.setMessageType(type);
      if ("IMAGE".equals(type)) {
        String img = message.getImageUrl() == null ? "" : message.getImageUrl();
        int comma = img.indexOf(',');
        if (br.com.personalfighters.util.StorageGuard.dbFull(jdbc)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "O envio de fotos está pausado no momento. Tente mais tarde.");
        boolean dataImg = img.length() <= 6_000_000 && comma > 0 && comma < 40
            && img.substring(0, comma + 1).matches("data:image/(jpeg|jpg|png|webp);base64,")
            && img.substring(comma + 1).chars().allMatch(ch -> Character.isLetterOrDigit(ch) && ch < 128 || ch == '+' || ch == '/' || ch == '=' || ch == '\n' || ch == '\r');
        if (!dataImg) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto pelo app.");
        // tira GPS/modelo do celular escondidos na foto antes de guardar
        try {
          byte[] raw = java.util.Base64.getMimeDecoder().decode(img.substring(comma + 1));
          byte[] clean = br.com.personalfighters.util.ImageMeta.strip(raw);
          if (clean != raw) message.setImageUrl(img.substring(0, comma + 1) + java.util.Base64.getEncoder().encodeToString(clean));
        } catch (IllegalArgumentException e) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie a foto pelo app."); }
      } else {
        message.setImageUrl(null); // texto e orçamento não levam link de imagem de fora
      }
      if ("SYSTEM".equals(type)) message.setImageUrl(null);
      if (!"QUOTE".equals(type)) {
        // campos de orçamento só existem em orçamento
        message.setQuoteStatus(null); message.setQuoteAmount(null); message.setQuoteTotalCost(null); message.setQuoteServiceCost(null);
        message.setQuoteMaterialsCost(null); message.setCounterOfferAmount(null); message.setCounterOfferStatus(null); message.setCounterOfferNote(null);
      }
      if (message.getText() != null && message.getText().length() > 4000) message.setText(message.getText().substring(0, 4000));
    }
    if ("QUOTE".equals(message.getMessageType())) normalizeQuote(message);
    try {
      return ResponseEntity.ok(messageService.send(message));
    } catch (MessageBlockedException e) {
      // O motivo exato fica só no registro do servidor (mostrar a regra ajudava a burlar o filtro)
      log.info("Mensagem bloqueada no pedido {}: {}", message.getOrderId(), e.getMatched());
      return ResponseEntity.status(422).body(Map.of(
          "blocked", true,
          "attemptNumber", e.getAttemptNumber(),
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
    if (service == null || service.compareTo(new BigDecimal("5")) < 0 || service.compareTo(max) > 0) {
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um valor válido para o serviço (mínimo R$ 5,00).");
    }
    BigDecimal materials = m.getQuoteMaterialsCost() == null ? BigDecimal.ZERO : m.getQuoteMaterialsCost().max(BigDecimal.ZERO).min(max).setScale(2, java.math.RoundingMode.HALF_UP);
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
    String status = String.valueOf(body.getOrDefault("status", "REJECTED")).trim().toUpperCase(java.util.Locale.ROOT);
    if (!"ACCEPTED".equals(status) && !"REJECTED".equals(status)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Responda aceitando ou recusando o orçamento.");
    return messageService.respondToQuote(id, status);
  }

  @PutMapping("/{id}/counter-offer")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Cliente faz contraproposta de valor.")
  public Message counterOffer(@PathVariable UUID id,
                              @RequestBody Map<String, Object> body) {
    need(roleInMessage(id), "CUSTOMER");
    BigDecimal amount;
    try { amount = new BigDecimal(body.getOrDefault("amount", "0").toString().replace(',', '.')).setScale(2, java.math.RoundingMode.HALF_UP); }
    catch (Exception e) { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido."); }
    // Contraproposta precisa ser um valor de verdade (antes aceitava -500 e o pedido ficava com total negativo)
    if (amount.compareTo(new BigDecimal("5")) < 0 || amount.compareTo(new BigDecimal("100000")) > 0)
      throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "A contraproposta precisa ser entre R$ 5 e R$ 100.000.");
    String note = body.getOrDefault("note", "").toString();
    if (note.length() > 500) note = note.substring(0, 500);
    return messageService.counterOffer(id, amount, note);
  }

  @PutMapping("/{id}/respond-counter")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Profissional aceita ou recusa contraproposta (ACCEPTED | REJECTED).")
  public Message respondToCounter(@PathVariable UUID id,
                                  @RequestBody Map<String, String> body) {
    need(roleInMessage(id), "PROFESSIONAL");
    String status = String.valueOf(body.getOrDefault("status", "REJECTED")).trim().toUpperCase(java.util.Locale.ROOT);
    if (!"ACCEPTED".equals(status) && !"REJECTED".equals(status)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Responda aceitando ou recusando a contraproposta.");
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
