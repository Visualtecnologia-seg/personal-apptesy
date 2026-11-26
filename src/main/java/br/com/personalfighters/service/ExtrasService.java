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
  @org.springframework.context.annotation.Lazy @org.springframework.beans.factory.annotation.Autowired private WalletService walletService;

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
    // Extra só se paga com o pedido em andamento (antes dava para pagar extra de pedido cancelado ou já encerrado e o dinheiro ficava parado)
    if (!open(order.getId())) throw new RuntimeException("Este pedido já foi encerrado. Não é possível pagar extras nele.");
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
    List<UUID> ids = jdbc.queryForList("SELECT id FROM order_extra_ WHERE pix_payment_id = ? AND status = 'PENDING_PAYMENT'", UUID.class, pixPaymentId);
    if (ids.isEmpty()) {
      // já pago (aviso repetido) conta como tratado; cancelado/devolvido segue para a devolução do webhook
      return jdbc.queryForObject("SELECT COUNT(*) FROM order_extra_ WHERE pix_payment_id = ? AND status = 'PAID'", Long.class, pixPaymentId) > 0;
    }
    UUID id = ids.get(0);
    UUID orderId = jdbc.queryForObject("SELECT order_id FROM order_extra_ WHERE id = ?", UUID.class, id);
    if (!open(orderId) && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE(pix_released, FALSE) FROM order_ WHERE id = ?", Boolean.class, orderId))) {
      jdbc.update("UPDATE order_extra_ SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING_PAYMENT'", id);
      return false; // pedido cancelado: o webhook devolve o PIX
    }
    markPaid(id);
    // Pago depois de o serviço já ter sido liberado: vai direto para a carteira (antes ficava preso para sempre)
    if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE(pix_released, FALSE) FROM order_ WHERE id = ?", Boolean.class, orderId))) {
      Map<String, Object> r = jdbc.queryForMap("SELECT e.service_value, e.pix_payment_id, o.professional_id FROM order_extra_ e JOIN order_ o ON o.id = e.order_id WHERE e.id = ?", id);
      if (jdbc.update("UPDATE order_extra_ SET released = TRUE WHERE id = ? AND released = FALSE", id) == 1)
        walletService.creditOnce(r.get("professional_id") == null ? null : ((Number) r.get("professional_id")).longValue(), "EXTRA", (BigDecimal) r.get("service_value"),
            orderId, "Extra do serviço #" + orderId.toString().substring(0, 8), String.valueOf(r.get("pix_payment_id")).startsWith("TESTE-"), "extra-" + id);
    }
    return true;
  }

  /** Pedido pago e ainda em andamento (aceita extras). */
  public boolean open(UUID orderId) {
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE id = ? AND COALESCE(pix_paid, FALSE) = TRUE AND COALESCE(pix_released, FALSE) = FALSE " +
        "AND status IN ('AWAITING_PAYMENT','IN_PROGRESS','PENDING_CLIENT_CONFIRMATION')", Long.class, orderId);
    return n != null && n > 0;
  }

  /** Na liberação do pagamento principal, libera também os extras pagos (para a carteira, uma única vez cada). */
  public void release(Order order) {
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, service_value, pix_payment_id FROM order_extra_ WHERE order_id = ? AND status = 'PAID' AND released = FALSE", order.getId());
    for (Map<String, Object> r : rows) {
      if (jdbc.update("UPDATE order_extra_ SET released = TRUE WHERE id = ? AND released = FALSE", r.get("id")) != 1) continue;
      boolean test = String.valueOf(r.get("pix_payment_id")).startsWith("TESTE-");
      walletService.creditOnce(order.getProfessional() == null ? null : order.getProfessional().getId(), "EXTRA", (BigDecimal) r.get("service_value"),
          order.getId(), "Extra do serviço #" + order.getId().toString().substring(0, 8), test, "extra-" + r.get("id"));
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
