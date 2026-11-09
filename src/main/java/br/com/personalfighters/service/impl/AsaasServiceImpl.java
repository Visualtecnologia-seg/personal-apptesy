package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AsaasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsaasServiceImpl implements AsaasService {

  private static final String APP_TAX_PERCENTAGE = "5";
  private static final double PROXIMITY_THRESHOLD_METERS = 300.0;

  @Value("${asaas.api.key:PLACEHOLDER_KEY}")
  private String asaasApiKey;

  @Value("${asaas.api.url:https://sandbox.asaas.com/api/v3}")
  private String asaasBaseUrl;

  private final TransactionRepository transactionRepository;
  private final UserRepository userRepository;

  private WebClient buildClient() {
    return WebClient.builder()
        .baseUrl(asaasBaseUrl)
        .defaultHeader("access_token", asaasApiKey)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @Override
  public String createOrGetCustomer(User user) {
    if (user.getAsaasCustomerId() != null && !user.getAsaasCustomerId().isBlank()) {
      return user.getAsaasCustomerId();
    }

    WebClient client = buildClient();
    Map<String, Object> body = new HashMap<>();
    body.put("name", user.getName() + (user.getSurname() != null ? " " + user.getSurname() : ""));
    body.put("email", user.getEmail());
    if (user.getCpf() != null && !user.getCpf().isBlank()) {
      body.put("cpfCnpj", user.getCpf().replaceAll("[^0-9]", ""));
    }
    if (user.getPhoneNumber() != null) {
      body.put("mobilePhone", user.getPhoneNumber().replaceAll("[^0-9]", ""));
    }

    try {
      Map response = client.post()
          .uri("/customers")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(Map.class)
          .block();

      String customerId = response != null ? (String) response.get("id") : null;
      if (customerId != null) {
        user.setAsaasCustomerId(customerId);
        userRepository.save(user);
      }
      return customerId;
    } catch (Exception e) {
      log.error("Erro ao criar cliente no Asaas: {}", e.getMessage());
      return null;
    }
  }

  @Override
  public boolean isTestMode() {
    return asaasApiKey == null || asaasApiKey.isBlank()
        || asaasApiKey.contains("CHAVE_NAO") || asaasApiKey.contains("PLACEHOLDER");
  }

  @Override
  public Map<String, String> createExtraCharge(User customer, BigDecimal value, String description) {
    Map<String, String> out = new HashMap<>();
    if (isTestMode()) {
      out.put("id", "TESTE-" + java.util.UUID.randomUUID());
      out.put("payload", "MODO TESTE");
      return out;
    }
    WebClient client = buildClient();
    Map<String, Object> body = new HashMap<>();
    body.put("billingType", "PIX");
    body.put("value", value.doubleValue());
    body.put("dueDate", LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    body.put("description", description);
    String cid = createOrGetCustomer(customer);
    if (cid != null) body.put("customer", cid);
    Map resp = client.post().uri("/payments").bodyValue(body).retrieve().bodyToMono(Map.class).block();
    if (resp == null || resp.get("id") == null) throw new RuntimeException("Erro ao gerar PIX. Tente novamente.");
    String id = (String) resp.get("id");
    out.put("id", id);
    try {
      Map qr = client.get().uri("/payments/" + id + "/pixQrCode").retrieve().bodyToMono(Map.class).block();
      if (qr != null) {
        out.put("payload", (String) qr.get("payload"));
        out.put("encodedImage", (String) qr.get("encodedImage"));
      }
    } catch (Exception e) {
      log.error("Erro ao obter QR Code PIX extra: {}", e.getMessage());
    }
    return out;
  }

  @Override
  public String refundPix(String asaasPaymentId, BigDecimal value) {
    if (asaasPaymentId == null || asaasPaymentId.isBlank()) return "Cobrança sem identificação no Asaas.";
    if (isTestMode() || asaasPaymentId.startsWith("TESTE-") || asaasPaymentId.startsWith("PKG-")) {
      log.info("Modo de teste: estorno de {} apenas registrado", asaasPaymentId);
      return null;
    }
    Map<String, Object> body = new HashMap<>();
    if (value != null) body.put("value", value.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
    body.put("description", "Estorno OPUS (pedido cancelado)");
    try {
      buildClient().post().uri("/payments/" + asaasPaymentId + "/refund").bodyValue(body).retrieve().bodyToMono(Map.class).block();
      return null;
    } catch (Exception e) {
      log.error("Erro ao estornar PIX {} no Asaas: {}", asaasPaymentId, e.getMessage());
      String m = e.getMessage() == null ? "erro" : e.getMessage();
      return m.length() > 280 ? m.substring(0, 280) : m;
    }
  }

  @Override
  public void cancelOpenCharges(Order order) {
    for (Transaction t : transactionRepository.findAllByOrderId(order.getId())) {
      if (t.getPaymentStatus() == PaymentStatus.AUTHORIZED && cancelCharge(t.getAsaasPaymentId())) {
        t.setPaymentStatus(PaymentStatus.REFUSED);
        transactionRepository.save(t);
      }
    }
  }

  @Override
  public boolean cancelCharge(String asaasPaymentId) {
    if (asaasPaymentId == null || asaasPaymentId.isBlank()) return true;
    if (isTestMode() || asaasPaymentId.startsWith("TESTE-") || asaasPaymentId.startsWith("PKG-")) return true;
    try {
      buildClient().delete().uri("/payments/" + asaasPaymentId).retrieve().bodyToMono(Map.class).block();
      return true;
    } catch (Exception e) {
      log.error("Erro ao cancelar cobrança {} no Asaas: {}", asaasPaymentId, e.getMessage());
      return false;
    }
  }

  @Override
  public boolean transferToProfessional(User professional, BigDecimal value, String description) {
    if (professional == null || value == null || value.compareTo(BigDecimal.ZERO) <= 0) return false;
    if (isTestMode()) {
      log.info("Modo de teste: transferência de R$ {} para {} apenas registrada", value, professional.getId());
      return true;
    }
    if (professional.getPixKey() == null || professional.getPixKey().isBlank()) return false;
    String cpfD = professional.getCpf() == null ? "" : professional.getCpf().replaceAll("\\D", "");
    if (cpfD.length() != 11 || !professional.getPixKey().replaceAll("\\D", "").equals(cpfD)) {
      log.warn("Transferência recusada: a chave PIX do profissional {} não é o CPF dele", professional.getId());
      return false;
    }
    Map<String, Object> body = new HashMap<>();
    body.put("value", value.doubleValue());
    body.put("pixAddressKey", professional.getPixKey());
    body.put("pixAddressKeyType", professional.getPixKeyType() != null ? professional.getPixKeyType() : "CPF");
    body.put("description", description);
    try {
      buildClient().post().uri("/transfers").bodyValue(body).retrieve().bodyToMono(Map.class).block();
      return true;
    } catch (Exception e) {
      log.error("Erro ao transferir PIX avulso: {}", e.getMessage());
      return false;
    }
  }

  @Override
  public Transaction createPixCharge(Order order) {
    // Só cobra pedido com profissional, ainda não pago e não encerrado
    if (Boolean.TRUE.equals(order.getPixPaid())) throw new IllegalStateException("Este pedido já foi pago.");
    if (order.getProfessional() == null || !(order.getStatus() == br.com.personalfighters.model.Status.OPEN
        || order.getStatus() == br.com.personalfighters.model.Status.CONFIRMED)) {
      throw new IllegalStateException("Este pedido não está aguardando pagamento.");
    }
    if (order.getTotalCost() == null || order.getTotalCost().signum() <= 0) throw new IllegalStateException("O valor deste pedido ainda não foi definido.");
    if (isTestMode()) return createTestCharge(order);
    // Reaproveita a cobrança aberta se o valor é o mesmo; se mudou, cancela a antiga antes de criar outra
    BigDecimal disc0 = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
    BigDecimal want = order.getTotalCost().subtract(disc0).max(BigDecimal.ONE);
    for (Transaction old : transactionRepository.findAllByOrderId(order.getId())) {
      if (old.getPaymentStatus() != PaymentStatus.AUTHORIZED) continue;
      boolean same = old.getTotalPaid() != null && old.getTotalPaid().compareTo(want) == 0
          && old.getPixExpiresAt() != null && old.getPixExpiresAt().isAfter(LocalDateTime.now().plusMinutes(30));
      if (same) return old;
      if (!cancelCharge(old.getAsaasPaymentId())) throw new IllegalStateException("Não foi possível atualizar a cobrança agora. Tente de novo.");
      old.setPaymentStatus(PaymentStatus.REFUSED);
      transactionRepository.save(old);
    }
    WebClient client = buildClient();
    User customer = order.getCustomer();
    String asaasCustomerId = createOrGetCustomer(customer);

    String dueDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    Map<String, Object> body = new HashMap<>();
    body.put("billingType", "PIX");
    BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
    BigDecimal charged = order.getTotalCost().subtract(discount).max(BigDecimal.ONE);
    body.put("value", charged.doubleValue());
    body.put("dueDate", dueDate);
    body.put("description", "Serviço Profissional Opus #" + order.getId().toString().substring(0, 8));
    if (asaasCustomerId != null) {
      body.put("customer", asaasCustomerId);
    }

    Map paymentResponse;
    try {
      paymentResponse = client.post()
          .uri("/payments")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(Map.class)
          .block();
    } catch (Exception e) {
      log.error("Erro ao criar cobrança PIX no Asaas: {}", e.getMessage());
      throw new RuntimeException("Erro ao gerar PIX. Tente novamente.");
    }

    if (paymentResponse == null || paymentResponse.get("id") == null) {
      throw new RuntimeException("Resposta inválida do Asaas ao criar cobrança PIX.");
    }

    String paymentId = (String) paymentResponse.get("id");

    Map qrResponse;
    try {
      qrResponse = client.get()
          .uri("/payments/" + paymentId + "/pixQrCode")
          .retrieve()
          .bodyToMono(Map.class)
          .block();
    } catch (Exception e) {
      log.error("Erro ao obter QR Code PIX: {}", e.getMessage());
      qrResponse = new HashMap();
    }

    String pixCopyPaste = qrResponse != null ? (String) qrResponse.get("payload") : null;
    String pixQrCode = qrResponse != null ? (String) qrResponse.get("encodedImage") : null;

    BigDecimal appTax = new BigDecimal(APP_TAX_PERCENTAGE);
    // Taxa embutida: o profissional recebe o valor cheio do serviço; a taxa (menos o desconto de pontos) fica com o OPUS
    BigDecimal professionalValue = order.getServiceValue() != null
        ? order.getServiceValue()
        : br.com.personalfighters.util.Pricing.serviceFromTotal(order.getTotalCost());
    BigDecimal appTaxValue = charged.subtract(professionalValue);

    Transaction transaction = Transaction.builder()
        .paymentStatus(PaymentStatus.AUTHORIZED)
        .paymentType(PaymentType.PIX)
        .asaasPaymentId(paymentId)
        .asaasCustomerId(asaasCustomerId)
        .pixQrCode(pixQrCode)
        .pixCopyPaste(pixCopyPaste)
        .pixExpiresAt(LocalDateTime.now().plusDays(1))
        .totalPaid(charged)
        .creditCardValue(BigDecimal.ZERO)
        .customerBalanceValue(BigDecimal.ZERO)
        .appTaxPercentage(appTax)
        .appTaxValue(appTaxValue)
        .professionalValue(professionalValue)
        .processingCost(BigDecimal.ZERO)
        .paymentMdrTax(BigDecimal.ZERO)
        .paymentMdrValue(BigDecimal.ZERO)
        .paymentTotalCost(BigDecimal.ZERO)
        .appProfit(BigDecimal.ZERO)
        .order(order)
        .customer(customer)
        .billingOrderDate(LocalDate.now())
        .orderIsDone(false)
        .professionalIsPaid(false)
        .build();

    return transactionRepository.save(transaction);
  }

  /** PIX simulado: a cobrança existe só no OPUS e é paga pelo botão "Simular pagamento". */
  private Transaction createTestCharge(Order order) {
    Optional<Transaction> existing = transactionRepository.findTransactionByOrderId(order.getId());
    if (existing.isPresent()) return existing.get();
    BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
    BigDecimal charged = order.getTotalCost().subtract(discount).max(BigDecimal.ONE);
    BigDecimal professionalValue = order.getServiceValue() != null
        ? order.getServiceValue()
        : br.com.personalfighters.util.Pricing.serviceFromTotal(order.getTotalCost());
    Transaction t = Transaction.builder()
        .paymentStatus(PaymentStatus.AUTHORIZED)
        .paymentType(PaymentType.PIX)
        .asaasPaymentId("TESTE-" + order.getId())
        .pixCopyPaste("MODO TESTE — use o botão Simular pagamento")
        .pixExpiresAt(LocalDateTime.now().plusDays(1))
        .totalPaid(charged)
        .creditCardValue(BigDecimal.ZERO)
        .customerBalanceValue(BigDecimal.ZERO)
        .appTaxPercentage(new BigDecimal(APP_TAX_PERCENTAGE))
        .appTaxValue(charged.subtract(professionalValue))
        .professionalValue(professionalValue)
        .processingCost(BigDecimal.ZERO)
        .paymentMdrTax(BigDecimal.ZERO)
        .paymentMdrValue(BigDecimal.ZERO)
        .paymentTotalCost(BigDecimal.ZERO)
        .appProfit(BigDecimal.ZERO)
        .order(order)
        .customer(order.getCustomer())
        .billingOrderDate(LocalDate.now())
        .orderIsDone(false)
        .professionalIsPaid(false)
        .build();
    return transactionRepository.save(t);
  }

  @Override
  public void releasePaymentToProfessional(Order order) {
    if (isTestMode()) {
      transactionRepository.findTransactionByOrderId(order.getId()).ifPresent(t -> {
        t.setProfessionalIsPaid(true);
        t.setProfessionalPaymentDate(LocalDate.now());
        t.setPaymentStatus(PaymentStatus.PAID);
        t.setProfessional(order.getProfessional());
        transactionRepository.save(t);
      });
      log.info("Modo de teste: liberação do pedido {} apenas registrada", order.getId());
      return;
    }
    User professional = order.getProfessional();
    if (professional == null) {
      log.warn("Pedido {} não tem profissional vinculado para receber pagamento", order.getId());
      return;
    }

    String pixKey = professional.getPixKey();
    String pixKeyType = professional.getPixKeyType();

    if (pixKey == null || pixKey.isBlank()) {
      log.warn("Profissional {} não tem chave PIX cadastrada", professional.getId());
      return;
    }

    Optional<Transaction> txOpt = transactionRepository.findTransactionByOrderId(order.getId());
    if (txOpt.isEmpty()) {
      log.warn("Transação não encontrada para pedido {}", order.getId());
      return;
    }

    Transaction transaction = txOpt.get();
    BigDecimal amount = transaction.getProfessionalValue();
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      log.warn("Valor inválido para transferência: {}", amount);
      return;
    }

    WebClient client = buildClient();
    Map<String, Object> body = new HashMap<>();
    body.put("value", amount.doubleValue());
    body.put("pixAddressKey", pixKey);
    body.put("pixAddressKeyType", pixKeyType != null ? pixKeyType : "CPF");
    body.put("description", "Pagamento serviço #" + order.getId().toString().substring(0, 8));

    try {
      Map response = client.post()
          .uri("/transfers")
          .bodyValue(body)
          .retrieve()
          .bodyToMono(Map.class)
          .block();

      if (response != null) {
        log.info("Transferência PIX realizada para profissional {}: R$ {}", professional.getId(), amount);
        transaction.setProfessionalIsPaid(true);
        transaction.setProfessionalPaymentDate(LocalDate.now());
        transaction.setPaymentStatus(PaymentStatus.PAID);
        transaction.setProfessional(professional);
        transactionRepository.save(transaction);
      }
    } catch (Exception e) {
      log.error("Erro ao transferir PIX para profissional {}: {}", professional.getId(), e.getMessage());
    }
  }

  public static double calculateDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
    final int R = 6371000;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }
}
