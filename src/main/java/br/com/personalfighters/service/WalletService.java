package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** Carteira do profissional: crédito quando o pagamento sai do cofre; saque no PIX quando ele quiser. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  private final JdbcTemplate jdbc;
  private final TransactionRepository transactionRepository;
  private final AsaasService asaasService;
  private final SecurityService securityService;
  private final PayoutService payoutService;
  private final EmailAutomationService emailAutomation;

  public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("America/Sao_Paulo")); }

  /** Coloca o valor do serviço na carteira do profissional (uma vez por pedido). */
  public void credit(Order order) {
    User pro = order.getProfessional();
    if (pro == null) return;
    BigDecimal amount = order.getServiceValue();
    Optional<Transaction> tx = transactionRepository.findTransactionByOrderId(order.getId());
    if (tx.isPresent() && tx.get().getProfessionalValue() != null && tx.get().getProfessionalValue().signum() > 0) {
      amount = tx.get().getProfessionalValue();
    }
    if (amount == null || amount.signum() <= 0) return;
    // Etapas já liberadas saem do valor final
    BigDecimal staged = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE order_id = ? AND kind = 'STAGE'", BigDecimal.class, order.getId());
    if (staged != null) amount = amount.subtract(staged);
    if (amount.signum() <= 0) return;
    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry_ WHERE order_id = ? AND kind = 'CREDIT'", Integer.class, order.getId());
    if (exists != null && exists > 0) return;
    jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at) VALUES (?, ?, 'CREDIT', ?, ?, 'DONE', ?, ?)",
        UUID.randomUUID(), pro.getId(), amount, order.getId(),
        "Serviço de " + (order.getCustomer() != null && order.getCustomer().getName() != null ? order.getCustomer().getName().trim().split("\\s+")[0] : "cliente"),
        Timestamp.valueOf(now()));
    emailAutomation.walletCredit(pro.getId(), amount, serviceName(order));
    tx.ifPresent(t -> {
      t.setProfessionalIsPaid(true);
      t.setProfessionalPaymentDate(LocalDate.now());
      t.setPaymentStatus(PaymentStatus.PAID);
      t.setProfessional(pro);
      transactionRepository.save(t);
    });
  }

  /** Libera uma etapa do serviço para a carteira (pagamento por etapas). */
  public void creditStage(Order order, int idx, BigDecimal amount) {
    User pro = order.getProfessional();
    if (pro == null || amount == null || amount.signum() <= 0) return;
    jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at) VALUES (?, ?, 'STAGE', ?, ?, 'DONE', ?, ?)",
        UUID.randomUUID(), pro.getId(), amount, order.getId(), "Etapa " + idx + " aprovada", Timestamp.valueOf(now()));
    emailAutomation.walletCredit(pro.getId(), amount, "Etapa " + idx + " de " + serviceName(order));
  }

  private static String serviceName(Order o) {
    String svc = o.getProduct() != null && o.getProduct().getName() != null ? o.getProduct().getName() : "Serviço";
    String c = o.getCustomer() != null && o.getCustomer().getName() != null ? o.getCustomer().getName().trim().split("\\s+")[0] : null;
    return c == null ? svc : svc + " para " + c;
  }

  public BigDecimal available(Long proId) {
    BigDecimal in = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE') AND status = 'DONE'", BigDecimal.class, proId);
    BigDecimal out = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'WITHDRAW' AND status <> 'FAILED'", BigDecimal.class, proId);
    return (in == null ? BigDecimal.ZERO : in).subtract(out == null ? BigDecimal.ZERO : out);
  }

  public String withdraw(User pro, BigDecimal amount) { return withdraw(pro, amount, "PIX"); }

  /**
   * Saque do próprio profissional: na chave PIX (CPF ou CNPJ conferido) ou na conta bancária no CPF/CNPJ dele.
   * Retorna o status final (DONE ou FAILED).
   */
  public synchronized String withdraw(User pro, BigDecimal amount, String to) {
    if (amount == null || amount.compareTo(BigDecimal.ONE) < 0) throw new IllegalArgumentException("O saque mínimo é R$ 1,00.");
    boolean toBank = "BANK".equalsIgnoreCase(to);
    // PIX_CPF / PIX_CNPJ: a chave é o próprio documento (sem trocar a chave guardada)
    String docKey = null;
    if ("PIX_CPF".equalsIgnoreCase(to)) {
      docKey = PayoutService.cpfOf(pro);
      if (docKey == null) throw new IllegalArgumentException("Não encontramos o CPF da sua conta. Fale com o suporte.");
    } else if ("PIX_CNPJ".equalsIgnoreCase(to)) {
      docKey = payoutService.verifiedCnpj(pro.getId());
      if (docKey == null) throw new IllegalArgumentException("Adicione e confirme o seu CNPJ antes.");
    }
    java.util.Map<String, Object> bank = null;
    if (docKey != null) {
      // nada a conferir: o documento é da própria conta
    } else if (toBank) {
      bank = payoutService.bank(pro.getId());
      if (bank == null) throw new IllegalArgumentException("Cadastre a sua conta bancária antes de sacar.");
      if (!payoutService.bankOk(pro, bank)) throw new IllegalArgumentException("A conta bancária precisa estar no seu CPF ou no seu CNPJ confirmado. Cadastre a conta de novo.");
    } else {
      if (pro.getPixKey() == null || pro.getPixKey().isBlank()) throw new IllegalArgumentException("Cadastre a sua chave PIX antes de sacar.");
      if (!payoutService.pixOk(pro))
        throw new IllegalArgumentException("O saque só vai para a chave PIX do seu CPF ou do seu CNPJ confirmado. Troque a chave e tente de novo.");
    }
    java.time.LocalDateTime hold = securityService.withdrawHoldUntil(pro.getId());
    if (hold != null) {
      securityService.event(pro.getId(), "WITHDRAW_HELD", "Tentou sacar R$ " + amount + " durante a espera de segurança");
      throw new IllegalArgumentException("Por segurança, como os dados de recebimento ou o celular foram trocados há pouco, o saque fica liberado em " +
          hold.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")) + ". O seu dinheiro continua guardado.");
    }
    if (amount.compareTo(available(pro.getId())) > 0) throw new IllegalArgumentException("Saldo insuficiente.");
    UUID id = UUID.randomUUID();
    String dest = docKey != null ? mask(docKey) : toBank ? bank.get("bank_name") + " " + mask(java.util.Objects.toString(bank.get("account"), "")) : mask(pro.getPixKey());
    jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, status, description, pix_key, created_at) VALUES (?, ?, 'WITHDRAW', ?, 'PENDING', ?, ?, ?)",
        id, pro.getId(), amount, toBank ? "Saque na conta" : "Saque PIX", dest, Timestamp.valueOf(now()));
    boolean ok;
    try {
      ok = docKey != null ? asaasService.transferPixToOwnDoc(pro, docKey, amount, "Saque OPUS")
          : toBank ? asaasService.transferToBank(pro, bank, amount, "Saque OPUS")
          : asaasService.isTestMode() || asaasService.transferToProfessional(pro, amount, "Saque OPUS");
    } catch (Exception e) {
      log.error("Saque do profissional {} falhou: {}", pro.getId(), e.getMessage());
      ok = false;
    }
    String status = ok ? "DONE" : "FAILED";
    jdbc.update("UPDATE wallet_entry_ SET status = ? WHERE id = ?", status, id);
    return status;
  }

  public static String mask(String key) {
    if (key == null) return null;
    String k = key.trim();
    if (k.length() <= 4) return "••••";
    if (k.contains("@")) return k.charAt(0) + "•••" + k.substring(k.indexOf('@'));
    return "•••• " + k.substring(k.length() - 4);
  }
}
