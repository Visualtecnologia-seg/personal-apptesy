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
  private final org.springframework.transaction.support.TransactionTemplate tx;

  /** Trava o pedido no banco enquanto o dinheiro dele entra na carteira (dois pedidos ao mesmo tempo não pagam duas vezes). */
  private <T> T lockedOrder(UUID orderId, java.util.function.Supplier<T> work) {
    return tx.execute(st -> {
      jdbc.queryForList("SELECT id FROM order_ WHERE id = ? FOR UPDATE", orderId);
      return work.get();
    });
  }

  public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("America/Sao_Paulo")); }

  /** Coloca o valor do serviço na carteira do profissional (uma vez por pedido). */
  public void credit(Order order) {
    if (order.getProfessional() == null) return;
    lockedOrder(order.getId(), () -> { doCredit(order); return null; });
  }

  private void doCredit(Order order) {
    User pro = order.getProfessional();
    BigDecimal amount = order.getServiceValue();
    // Usa a cobrança que foi PAGA de fato (não outra cobrança do mesmo pedido criada depois com outro valor)
    Optional<Transaction> tx = transactionRepository.findAllByOrderId(order.getId()).stream()
        .filter(t -> t.getPaymentStatus() == br.com.personalfighters.model.PaymentStatus.PAID)
        .reduce((a, b) -> b);
    if (tx.isEmpty()) tx = transactionRepository.findTransactionByOrderId(order.getId());
    if (tx.isPresent() && tx.get().getProfessionalValue() != null && tx.get().getProfessionalValue().signum() > 0) {
      amount = tx.get().getProfessionalValue();
    }
    // Nunca libera mais do que entrou na cobrança paga
    if (tx.isPresent() && tx.get().getTotalPaid() != null && tx.get().getTotalPaid().signum() > 0 && amount != null && amount.compareTo(tx.get().getTotalPaid()) > 0)
      amount = tx.get().getTotalPaid();
    if (amount == null || amount.signum() <= 0) return;
    // Etapas já liberadas saem do valor final
    BigDecimal staged = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE order_id = ? AND kind = 'STAGE'", BigDecimal.class, order.getId());
    if (staged != null) amount = amount.subtract(staged);
    if (amount.signum() <= 0) return;
    Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry_ WHERE order_id = ? AND kind = 'CREDIT'", Integer.class, order.getId());
    if (exists != null && exists > 0) return;
    int n = jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test, ref) VALUES (?, ?, 'CREDIT', ?, ?, 'DONE', ?, ?, ?, ?) ON CONFLICT DO NOTHING",
        UUID.randomUUID(), pro.getId(), amount, order.getId(),
        "Serviço de " + (order.getCustomer() != null && order.getCustomer().getName() != null ? order.getCustomer().getName().trim().split("\\s+")[0] : "cliente"),
        Timestamp.valueOf(now()), testMoney(order), "credit-" + order.getId());
    if (n == 0) return;
    emailAutomation.walletCredit(pro.getId(), amount, serviceName(order));
    tx.ifPresent(t -> {
      t.setProfessionalIsPaid(true);
      t.setProfessionalPaymentDate(LocalDate.now());
      t.setPaymentStatus(PaymentStatus.PAID);
      t.setProfessional(pro);
      transactionRepository.save(t);
    });
  }

  /**
   * Dinheiro devolvido ao cliente depois de já ter ido para o profissional (etapas, crédito ou extras):
   * desconta da carteira dele (fica negativa se ele já sacou e é compensada nos próximos serviços).
   * Antes o cliente recebia de volta e o profissional ficava com o valor: o OPUS pagava duas vezes.
   */
  public void reverseOrder(Order order, String why) {
    // Dinheiro devolvido ao cliente: os pontos que ele ganhou com esse pedido saem também (uma vez só)
    jdbc.update("INSERT INTO points_ (user_id, amount, reason, order_id, created_at) " +
        "SELECT user_id, -SUM(amount), 'Pontos do pedido devolvido', order_id, NOW() FROM points_ WHERE order_id = ? AND amount > 0 AND reason <> 'Pontos do pedido devolvido' " +
        "AND NOT EXISTS (SELECT 1 FROM points_ x WHERE x.order_id = ? AND x.reason = 'Pontos do pedido devolvido') GROUP BY user_id, order_id", order.getId(), order.getId());
    User pro = order.getProfessional();
    if (pro == null) return;
    BigDecimal got = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE order_id = ? AND kind IN ('CREDIT','STAGE','EXTRA') AND status = 'DONE'", BigDecimal.class, order.getId());
    // extras antigos, pagos por PIX direto (antes da carteira)
    BigDecimal extras = jdbc.queryForObject("SELECT COALESCE(SUM(x.service_value),0) FROM order_extra_ x WHERE x.order_id = ? AND x.released = TRUE " +
        "AND NOT EXISTS (SELECT 1 FROM wallet_entry_ w WHERE w.ref = 'extra-' || x.id::text)", BigDecimal.class, order.getId());
    BigDecimal back = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE order_id = ? AND kind = 'REVERSAL'", BigDecimal.class, order.getId());
    BigDecimal due = (got == null ? BigDecimal.ZERO : got).add(extras == null ? BigDecimal.ZERO : extras).subtract(back == null ? BigDecimal.ZERO : back);
    if (due.signum() <= 0) return;
    jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test) VALUES (?, ?, 'REVERSAL', ?, ?, 'DONE', ?, ?, ?)",
        UUID.randomUUID(), pro.getId(), due, order.getId(), "Devolvido ao cliente: " + why, Timestamp.valueOf(now()), testMoney(order));
    log.warn("Pedido {}: R$ {} descontado da carteira do profissional {} ({})", order.getId(), due, pro.getId(), why);
  }

  /**
   * Crédito avulso na carteira com identificador único (ref): o mesmo crédito nunca entra duas vezes,
   * mesmo com pedidos ao mesmo tempo. kind: EXTRA (orçamento extra), TRIP (taxa de deslocamento), CLASS (aula em grupo).
   */
  public boolean creditOnce(Long proId, String kind, BigDecimal amount, UUID orderId, String description, boolean test, String ref) {
    if (proId == null || amount == null || amount.signum() <= 0) return false;
    int n = jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test, ref) VALUES (?, ?, ?, ?, ?, 'DONE', ?, ?, ?, ?) ON CONFLICT DO NOTHING",
        UUID.randomUUID(), proId, kind, amount, orderId, description, Timestamp.valueOf(now()), test || asaasService.isTestMode(), ref);
    return n == 1;
  }

  /** Dinheiro do modo de teste (pago com PIX simulado) neste pedido? Nunca vira dinheiro real. */
  public boolean isTestMoney(Order order) { return testMoney(order); }

  /** Libera uma etapa do serviço para a carteira (pagamento por etapas). */
  public boolean creditStage(Order order, int idx, BigDecimal amount) {
    User pro = order.getProfessional();
    if (pro == null || amount == null || amount.signum() <= 0) return false;
    Boolean ok = lockedOrder(order.getId(), () -> {
      // Depois que o valor final foi para a carteira (ou o pedido acabou/foi cancelado), etapa nenhuma paga mais nada
      java.util.Map<String, Object> o = jdbc.queryForMap("SELECT status, COALESCE(pix_paid, FALSE) AS paid, COALESCE(pix_released, FALSE) AS released, service_value, dispute_status FROM order_ WHERE id = ?", order.getId());
      String st = java.util.Objects.toString(o.get("status"), "");
      if (!Boolean.TRUE.equals(o.get("paid")) || Boolean.TRUE.equals(o.get("released")) || "OPEN".equalsIgnoreCase(java.util.Objects.toString(o.get("dispute_status"), ""))
          || !java.util.Set.of("AWAITING_PAYMENT", "IN_PROGRESS", "PENDING_CLIENT_CONFIRMATION").contains(st)) return false;
      Integer credited = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry_ WHERE order_id = ? AND kind = 'CREDIT'", Integer.class, order.getId());
      if (credited != null && credited > 0) return false;
      BigDecimal staged = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE order_id = ? AND kind = 'STAGE'", BigDecimal.class, order.getId());
      BigDecimal cap = o.get("service_value") == null ? BigDecimal.ZERO : (BigDecimal) o.get("service_value");
      if (staged.add(amount).compareTo(cap) > 0) return false;
      return jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, order_id, status, description, created_at, test, ref) VALUES (?, ?, 'STAGE', ?, ?, 'DONE', ?, ?, ?, ?) ON CONFLICT DO NOTHING",
          UUID.randomUUID(), pro.getId(), amount, order.getId(), "Etapa " + idx + " aprovada", Timestamp.valueOf(now()), testMoney(order), "stage-" + order.getId() + "-" + idx) == 1;
    });
    if (Boolean.TRUE.equals(ok)) emailAutomation.walletCredit(pro.getId(), amount, "Etapa " + idx + " de " + serviceName(order));
    return Boolean.TRUE.equals(ok);
  }

  /** Pedido pago com PIX simulado (ou pagamento ainda em modo de teste)? */
  private boolean testMoney(Order order) {
    if (asaasService.isTestMode()) return true;
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM transaction_ WHERE order_id = ? AND asaas_payment_id LIKE 'TESTE-%'", Integer.class, order.getId());
    if (n != null && n > 0) return true;
    // aula de pacote: o pacote foi pago com PIX simulado?
    Integer p = jdbc.queryForObject("SELECT COUNT(*) FROM order_ o JOIN package_purchase_ pp ON pp.id = o.package_purchase_id WHERE o.id = ? AND (pp.pix_payment_id IS NULL OR pp.pix_payment_id LIKE 'TESTE-%')", Integer.class, order.getId());
    return p != null && p > 0;
  }

  private static String serviceName(Order o) {
    String svc = o.getProduct() != null && o.getProduct().getName() != null ? o.getProduct().getName() : "Serviço";
    String c = o.getCustomer() != null && o.getCustomer().getName() != null ? o.getCustomer().getName().trim().split("\\s+")[0] : null;
    return c == null ? svc : svc + " para " + c;
  }

  public BigDecimal available(Long proId) {
    // Com o pagamento de verdade ligado, o dinheiro do modo de teste não conta (nunca vira saque real)
    String real = asaasService.isTestMode() ? "" : " AND test = FALSE";
    BigDecimal in = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('CREDIT','STAGE','CLASS','EXTRA','TRIP') AND status = 'DONE'" + real, BigDecimal.class, proId);
    BigDecimal out = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM wallet_entry_ WHERE professional_id = ? AND kind IN ('WITHDRAW','REVERSAL') AND status <> 'FAILED'" + real, BigDecimal.class, proId);
    return (in == null ? BigDecimal.ZERO : in).subtract(out == null ? BigDecimal.ZERO : out);
  }

  public String withdraw(User pro, BigDecimal amount) { return withdraw(pro, amount, "PIX"); }

  /**
   * Saque do próprio profissional: na chave PIX (CPF ou CNPJ conferido) ou na conta bancária no CPF/CNPJ dele.
   * Retorna o status final (DONE ou FAILED).
   */
  private final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.locks.ReentrantLock> locks = new java.util.concurrent.ConcurrentHashMap<>();
  /** No máximo 2 saques conversando com o banco ao mesmo tempo no servidor todo (cada um segura uma conexão do banco de dados). */
  private final java.util.concurrent.Semaphore banking = new java.util.concurrent.Semaphore(2, true);

  public String withdraw(User pro, BigDecimal amount, String to) {
    // Conta bloqueada, desativada ou apagada não saca
    Integer okAcc = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE id = ? AND COALESCE(blocked, FALSE) = FALSE AND COALESCE(active, FALSE) = TRUE AND deleted_at IS NULL", Integer.class, pro.getId());
    if (okAcc == null || okAcc == 0) throw new IllegalArgumentException("Esta conta não pode fazer saques agora. Fale com o suporte.");
    // Um saque por profissional de cada vez; o segundo clique responde na hora em vez de ficar esperando
    // (antes, vários cliques seguidos ocupavam todas as conexões e o app ficava fora do ar para todo mundo)
    java.util.concurrent.locks.ReentrantLock l = locks.computeIfAbsent(pro.getId(), k -> new java.util.concurrent.locks.ReentrantLock());
    if (!l.tryLock()) throw new IllegalArgumentException("Já tem um saque seu em andamento. Aguarde a confirmação.");
    try {
      boolean got;
      try { got = banking.tryAcquire(8, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); got = false; }
      if (!got) throw new IllegalArgumentException("Muitos saques sendo feitos agora. Tente de novo em 1 minuto; o seu dinheiro continua na carteira.");
      try { return doWithdraw(pro, amount, to); } finally { banking.release(); }
    } finally {
      l.unlock();
    }
  }

  private String doWithdraw(User pro, BigDecimal amount, String to) {
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
    UUID id = UUID.randomUUID();
    String dest = docKey != null ? mask(docKey) : toBank ? bank.get("bank_name") + " " + mask(java.util.Objects.toString(bank.get("account"), "")) : mask(pro.getPixKey());
    // Confere o saldo e reserva o valor de uma vez, com trava no banco (vale mesmo com duas cópias do servidor no ar)
    boolean reserved = Boolean.TRUE.equals(tx.execute(st -> {
      jdbc.queryForList("SELECT pg_advisory_xact_lock(?, ?)", 7301, pro.getId().intValue());
      if (amount.compareTo(available(pro.getId())) > 0) return false;
      jdbc.update("INSERT INTO wallet_entry_ (id, professional_id, kind, amount, status, description, pix_key, created_at, test) VALUES (?, ?, 'WITHDRAW', ?, 'PENDING', ?, ?, ?, ?)",
          id, pro.getId(), amount, toBank ? "Saque na conta" : "Saque PIX", dest, Timestamp.valueOf(now()), asaasService.isTestMode());
      return true;
    }));
    if (!reserved) throw new IllegalArgumentException("Saldo insuficiente.");
    boolean ok;
    String ref = "saque-" + id; // o Asaas não repete uma transferência com o mesmo identificador
    try {
      ok = docKey != null ? asaasService.transferPixToOwnDoc(pro, docKey, amount, "Saque OPUS", ref)
          : toBank ? asaasService.transferToBank(pro, bank, amount, "Saque OPUS", ref)
          : asaasService.isTestMode() || asaasService.transferToProfessional(pro, amount, "Saque OPUS", ref);
    } catch (AsaasService.UncertainTransfer e) {
      // Pode ter saído: o valor NÃO volta para o saldo até a equipe conferir no Asaas (antes voltava e dava para sacar de novo)
      log.error("Saque {} do profissional {} sem confirmação: {}", id, pro.getId(), e.getMessage());
      jdbc.update("UPDATE wallet_entry_ SET status = 'REVIEW' WHERE id = ?", id);
      jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, 'SAQUE_CONFERIR', ?, NOW())",
          pro.getId(), "Saque de R$ " + amount + " sem resposta do Asaas (ref " + ref + "): conferir se saiu");
      return "REVIEW";
    } catch (Exception e) {
      log.error("Saque do profissional {} falhou: {}", pro.getId(), e.getMessage());
      ok = false;
    }
    String status = ok ? "DONE" : "FAILED";
    jdbc.update("UPDATE wallet_entry_ SET status = ? WHERE id = ?", status, id);
    emailAutomation.withdrawResult(pro.getId(), amount, dest, ok);
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
