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
  public Transaction createPixCharge(Order order) {
    WebClient client = buildClient();
    User customer = order.getCustomer();
    String asaasCustomerId = createOrGetCustomer(customer);

    String dueDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    Map<String, Object> body = new HashMap<>();
    body.put("billingType", "PIX");
    body.put("value", order.getTotalCost().doubleValue());
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
    BigDecimal appTaxValue = order.getTotalCost()
        .multiply(appTax.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
    BigDecimal professionalValue = order.getTotalCost().subtract(appTaxValue);

    Transaction transaction = Transaction.builder()
        .paymentStatus(PaymentStatus.AUTHORIZED)
        .paymentType(PaymentType.PIX)
        .asaasPaymentId(paymentId)
        .asaasCustomerId(asaasCustomerId)
        .pixQrCode(pixQrCode)
        .pixCopyPaste(pixCopyPaste)
        .pixExpiresAt(LocalDateTime.now().plusDays(1))
        .totalPaid(order.getTotalCost())
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

  @Override
  public void releasePaymentToProfessional(Order order) {
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
