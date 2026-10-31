package br.com.personalfighters.service;

import br.com.personalfighters.entity.Message;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.repository.MessageRepository;
import br.com.personalfighters.util.Pricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orçamento extra durante o serviço: quando o cliente aceita um orçamento num pedido já pago,
 * o valor vira uma cobrança à parte (com a taxa embutida) e fica guardado até a conclusão.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtrasService {

  private final JdbcTemplate jdbc;
  private final AsaasService asaasService;
  private final MessageRepository messageRepository;
  private final NotificationService notificationService;

  /** O pedido já foi pago? Então um novo orçamento aceito é "extra". */
  public boolean isExtra(Order order) {
    return Boolean.TRUE.equals(order.getPixPaid());
  }

  public UUID createFromQuote(Order order, Message quote, BigDecimal serviceValue) {
    UUID id = UUID.randomUUID();
    String desc = quote.getQuoteDescription() != null && !quote.getQuoteDescription().isBlank()
        ? quote.getQuoteDescription() : "Orçamento extra";
    jdbc.update("INSERT INTO order_extra_ (id, order_id, message_id, description, service_value, client_total, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?)",
        id, order.getId(), quote.getId(), desc, serviceValue, Pricing.clientTotal(serviceValue), Timestamp.valueOf(LocalDateTime.now()));
    system(order.getId(), "➕ Orçamento extra aceito: R$ " + Pricing.clientTotal(serviceValue).setScale(2) + ". Aguardando o pagamento do cliente.");
    return id;
  }

  public List<Map<String, Object>> list(UUID orderId) {
    return jdbc.queryForList(
        "SELECT id, description, service_value AS \"serviceValue\", client_total AS \"clientTotal\", status, " +
            "pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\", created_at AS \"createdAt\", paid_at AS \"paidAt\", released " +
            "FROM order_extra_ WHERE order_id = ? ORDER BY created_at", orderId);
  }

  public Map<String, Object> charge(Order order, UUID extraId) {
    Map<String, Object> e = jdbc.queryForMap("SELECT * FROM order_extra_ WHERE id = ? AND order_id = ?", extraId, order.getId());
    if (!"PENDING_PAYMENT".equals(e.get("status"))) throw new RuntimeException("Este extra já foi pago.");
    if (e.get("pix_payment_id") == null) {
      Map<String, String> c = asaasService.createExtraCharge(order.getCustomer(), (BigDecimal) e.get("client_total"),
          "Extra do serviço Opus #" + order.getId().toString().substring(0, 8));
      jdbc.update("UPDATE order_extra_ SET pix_payment_id = ?, pix_copy_paste = ?, pix_qr = ? WHERE id = ?",
          c.get("id"), c.get("payload"), c.get("encodedImage"), extraId);
    }
    return jdbc.queryForMap(
        "SELECT id, client_total AS \"clientTotal\", status, pix_copy_paste AS \"pixCopyPaste\", pix_qr AS \"pixQrCode\" FROM order_extra_ WHERE id = ?", extraId);
  }

  public boolean markPaid(UUID extraId) {
    int n = jdbc.update("UPDATE order_extra_ SET status = 'PAID', paid_at = ? WHERE id = ? AND status = 'PENDING_PAYMENT'",
        Timestamp.valueOf(LocalDateTime.now()), extraId);
    if (n > 0) {
      Map<String, Object> e = jdbc.queryForMap("SELECT order_id, client_total FROM order_extra_ WHERE id = ?", extraId);
      system((UUID) e.get("order_id"), "✅ Extra de R$ " + ((BigDecimal) e.get("client_total")).setScale(2) + " pago. O valor fica guardado até o fim do serviço.");
    }
    return n > 0;
  }

  /** Webhook do Asaas: a cobrança paga pode ser de um extra. */
  public boolean markPaidByPixId(String pixPaymentId) {
    List<UUID> ids = jdbc.queryForList("SELECT id FROM order_extra_ WHERE pix_payment_id = ?", UUID.class, pixPaymentId);
    if (ids.isEmpty()) return false;
    markPaid(ids.get(0));
    return true;
  }

  /** Na liberação do pagamento principal, libera também os extras pagos. */
  public void release(Order order) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, service_value FROM order_extra_ WHERE order_id = ? AND status = 'PAID' AND released = FALSE", order.getId());
    for (Map<String, Object> r : rows) {
      boolean ok = asaasService.transferToProfessional(order.getProfessional(), (BigDecimal) r.get("service_value"),
          "Extra do serviço #" + order.getId().toString().substring(0, 8));
      if (ok) jdbc.update("UPDATE order_extra_ SET released = TRUE WHERE id = ?", r.get("id"));
    }
    jdbc.update("UPDATE order_extra_ SET status = 'CANCELLED' WHERE order_id = ? AND status = 'PENDING_PAYMENT'", order.getId());
  }

  public BigDecimal paidTotal(UUID orderId) {
    BigDecimal v = jdbc.queryForObject("SELECT COALESCE(SUM(client_total), 0) FROM order_extra_ WHERE order_id = ? AND status = 'PAID'", BigDecimal.class, orderId);
    return v == null ? BigDecimal.ZERO : v;
  }

  private void system(UUID orderId, String text) {
    try {
      messageRepository.save(Message.builder().orderId(orderId).senderName("OPUS").senderRole("SYSTEM").messageType("SYSTEM").text(text).build());
    } catch (Exception e) {
      log.warn("Mensagem do sistema não salva: {}", e.getMessage());
    }
  }
}
