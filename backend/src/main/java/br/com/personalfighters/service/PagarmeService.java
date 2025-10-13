package br.com.personalfighters.service;

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
import reactor.core.publisher.Flux;

public interface PagarmeService {

  @Deprecated
  Card saveCard(CardRequest cardRequest);

  @Deprecated
  Card saveCardUnsafe(CardUnsafeRequest cardRequest);

  CardResponse saveCardUnsafe(String customerId, CreateCardRequest cardRequest);

  @Deprecated
  Flux<Card> getCustomerCards(String customerId);

  @Deprecated
  CustomerPagarme getCustomer(String customerId);

  CustomerResponse getCustomerV5(String customerId);

  @Deprecated
  CustomerPagarme saveCustomer(CustomerPagarmeRequest customerPagarmeRequest);

  CustomerResponse saveCustomer(CustomerRequest customerRequest);

  @Deprecated
  TransactionPagarme createTransaction(TransactionRequest transactionRequest);

  OrderResponse createOrder(OrderRequest orderRequest);

  @Deprecated
  TransactionPagarme captureTransaction(
      Integer transactionId, CaptureTransactionRequest captureTransactionRequest);

  ChargeResponse captureChargeOrder(String transactionId, CaptureChargeRequest captureChargeRequest);

  @Deprecated
  TransactionPagarme refundTransaction(Integer transactionId);

  ChargeResponse cancelAndRefundChargeOrder(String transactionId, CancelChargeRequest cancelChargeRequest);

  @Deprecated
  CardHashKey getCardHashKey();
}
