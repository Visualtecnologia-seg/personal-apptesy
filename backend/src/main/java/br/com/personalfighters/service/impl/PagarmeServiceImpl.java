package br.com.personalfighters.service.impl;

import br.com.personalfighters.model.pagarme.Card;
import br.com.personalfighters.model.pagarme.CardHashKey;
import br.com.personalfighters.model.pagarme.CustomerPagarme;
import br.com.personalfighters.model.pagarme.TransactionPagarme;
import br.com.personalfighters.model.pagarme.request.*;
import br.com.personalfighters.model.pagarmev5.CardResponse;
import br.com.personalfighters.model.pagarmev5.ChargeResponse;
import br.com.personalfighters.model.pagarmev5.CustomerResponse;
import br.com.personalfighters.model.pagarmev5.OrderResponse;
import br.com.personalfighters.model.pagarmev5.request.*;
import br.com.personalfighters.service.PagarmeService;
import br.com.personalfighters.service.impl.exceptions.PaymentException;
import br.com.personalfighters.service.impl.properties.PagarmeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class PagarmeServiceImpl implements PagarmeService {

  private final PagarmeProperties properties;

  private final WebClient webClient;

  @Override
  public Card saveCard(CardRequest cardRequest) {
    return webClient
        .post()
        .uri("/cards")
        .body(BodyInserters.fromValue(cardRequest))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(Card.class).block();
  }

  @Override
  public Card saveCardUnsafe(CardUnsafeRequest cardRequest) {
    return webClient
        .post()
        .uri("/cards")
        .body(BodyInserters.fromValue(cardRequest))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(Card.class).block();
  }

  @Override
  public CardResponse saveCardUnsafe(String customerId, CreateCardRequest cardRequest) {
    return webClient
        .post()
        .uri(String.format(properties.getCustomersCardsEnpoint(), customerId))
        .body(BodyInserters.fromValue(cardRequest))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(CardResponse.class)
        .block();
  }

  @Override
  public Flux<Card> getCustomerCards(String customerId) {
    return webClient
        .get()
        .uri("/cards?customer_id=" + customerId)
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToFlux(Card.class);
  }

  @Override
  public CustomerPagarme getCustomer(String customerId) {
    return webClient
        .get()
        .uri("/customers/" + customerId)
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(CustomerPagarme.class)
        .block();
  }

  @Override
  public CustomerResponse getCustomerV5(String customerId) {
    return webClient
        .get()
        .uri(properties.getCustomersEndpoint().concat("/" + customerId))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(CustomerResponse.class)
        .block();
  }

  @Override
  public CustomerPagarme saveCustomer(CustomerPagarmeRequest customerPagarmeRequest) {
    Mono<CustomerPagarme> monoCustomer = webClient
        .post()
        .uri("/customers")
        .body(BodyInserters.fromValue(customerPagarmeRequest))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(CustomerPagarme.class);

    return monoCustomer.block();
  }

  @Override
  public CustomerResponse saveCustomer(CustomerRequest customerRequest) {
    Mono<CustomerResponse> monoCustomer =
        webClient
            .post()
            .uri(properties.getCustomersEndpoint())
            .body(BodyInserters.fromValue(customerRequest))
            .retrieve()
            .onStatus(HttpStatus::isError, this::handlePaymentError)
            .bodyToMono(CustomerResponse.class);
    return monoCustomer.block();
  }

  @Override
  public TransactionPagarme createTransaction(TransactionRequest transactionRequest) {

    CustomerPagarme customer = getCustomer(transactionRequest.getCustomerId());

    TransactionBody transactionBody = new TransactionBody(transactionRequest, customer);

    Mono<TransactionPagarme> monoTransaction = webClient
        .post()
        .uri("/transactions")
        .body(BodyInserters.fromValue(transactionBody))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(TransactionPagarme.class);
    return monoTransaction.block();
  }

  @Override
  public OrderResponse createOrder(OrderRequest orderRequest) {
    Mono<OrderResponse> monoTransaction =
        webClient
            .post()
            .uri(properties.getOrdersEndpoint())
            .body(BodyInserters.fromValue(orderRequest))
            .retrieve()
            .onStatus(HttpStatus::isError, this::handlePaymentError)
                .onStatus(httpStatus -> httpStatus.value() == 422, this::handlePaymentError)
            .bodyToMono(OrderResponse.class);
    return monoTransaction.block();

  }

  @Override
  public TransactionPagarme captureTransaction(Integer transactionId, CaptureTransactionRequest captureTransactionRequest) {

    Mono<TransactionPagarme> monoTransaction = webClient
        .post()
        .uri("/transactions/" + transactionId + "/capture")
        .body(BodyInserters.fromValue(captureTransactionRequest))
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(TransactionPagarme.class);
    return monoTransaction.block();
  }

  @Override
  public ChargeResponse captureChargeOrder(String transactionId, CaptureChargeRequest captureChargeRequest) {
    Mono<ChargeResponse> monoTransaction = webClient
            .post()
            .uri(String.format(properties.getOrdersChargeCaptureEndpoint(), transactionId))
            .body(BodyInserters.fromValue(captureChargeRequest))
            .retrieve()
            .onStatus(HttpStatus::isError, this::handlePaymentError)
            .bodyToMono(ChargeResponse.class);
    return monoTransaction.block();
  }

  @Override
  public TransactionPagarme refundTransaction(Integer transactionId) {
    Mono<TransactionPagarme> monoTransaction = webClient
        .post()
        .uri("/transactions/" + transactionId + "/refund")
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(TransactionPagarme.class);
    return monoTransaction.block();
  }

  @Override
  public ChargeResponse cancelAndRefundChargeOrder(String transactionId, CancelChargeRequest cancelChargeRequest) {
    Mono<ChargeResponse> monoTransaction = webClient
            .method(HttpMethod.DELETE)
            .uri(String.format(properties.getOrdersChargeRefundEndpoint(), transactionId))
            .body(BodyInserters.fromValue(cancelChargeRequest))
            .retrieve()
            .onStatus(HttpStatus::isError, this::handlePaymentError)
            .bodyToMono(ChargeResponse.class);
    return monoTransaction.block();
  }

  @Override
  public CardHashKey getCardHashKey() {
    Mono<CardHashKey> monoTransaction = webClient
        .get()
        .uri("/transactions/card_hash_key")
        .retrieve()
        .onStatus(HttpStatus::isError, this::handlePaymentError)
        .bodyToMono(CardHashKey.class);
    return monoTransaction.block();
  }

  private Mono<? extends Throwable> handlePaymentError(ClientResponse clientResponse) {
    var errors = clientResponse.bodyToMono(Object.class);
    return errors.flatMap(error -> {
      log.error(error.toString());
      return Mono.error(new PaymentException("Payment error"));
    });
  }
}
