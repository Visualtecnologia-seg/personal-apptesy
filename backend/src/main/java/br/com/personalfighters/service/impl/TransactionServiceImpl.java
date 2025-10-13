package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;
import br.com.personalfighters.model.PaymentStatus;
import br.com.personalfighters.model.PaymentType;
import br.com.personalfighters.model.pagarme.Billing;
import br.com.personalfighters.model.pagarme.request.AddressBilling;
import br.com.personalfighters.model.pagarmev5.OrderResponse;
import br.com.personalfighters.model.pagarmev5.request.*;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.TransactionRepository;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.service.PagarmeService;
import br.com.personalfighters.service.TransactionService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import br.com.personalfighters.service.impl.exceptions.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

  //Taxa percentual do Personal Fighter
  private static final String appTax = "25";
  private final TransactionRepository transactionRepository;
  private final FinanceService financeService;
  private final PagarmeService pagarmeService;
  private final OrderRepository orderRepository;

  @Override
  public Optional<Transaction> find(Long id) {
    return transactionRepository.findById(id);
  }

  @Override
  public Optional<Transaction> findByOrderId(UUID id) {
    return transactionRepository.findTransactionByOrderId(id);
  }

  @Override
  public Transaction save(Transaction transaction) {
    return transactionRepository.save(transaction);
  }

  @Override
  public Transaction doHoldPayment(Order order) {
    BigDecimal creditCardValue = new BigDecimal(String.valueOf(order.getTotalCost()));
    var orderPagarme = holdPayment(order, creditCardValue);

    if (orderPagarme.getStatus().equals("pending")) {
      Transaction transaction = Transaction.builder()
          .paymentStatus(PaymentStatus.AUTHORIZED)
          .paymentType(PaymentType.CREDIT_CARD)
          .paymentTransactionId(orderPagarme.getCharges().get(0).getId())
          .creditCardValue(creditCardValue)
          .totalPaid(order.getTotalCost())
          .order(order)
          .acquirerName(orderPagarme.getCharges().get(0).getLastTransaction().getAcquirerName())
          .customer(order.getCustomer())
          .card(order.getPayment().getCard())
          .processingCost(new BigDecimal("0.49"))
          .billingOrderDate(order.getDate())
          .build();
      return save(transaction);
    } else {
      throw new PaymentException("Payment not authorized.");
    }
  }

  @Override
  public Transaction doPayment(Order order) {
    Transaction transaction = findByOrderId(order.getId()).orElseThrow();
    var capture = CaptureChargeRequest.builder()
        .amount(convertBigDecimalToIntegerPagarme(transaction.getCreditCardValue()))
        .build();

    try {
      var chargeResponse = pagarmeService.captureChargeOrder(transaction.getPaymentTransactionId(), capture);
      if (!Objects.equals(chargeResponse.getStatus(), "paid")) {
        throw new PaymentException(String.format("Charge status is %s", chargeResponse.getStatus()));
      }
    } catch (PaymentException e) {
      throw new PaymentException(e.getLocalizedMessage());
    }

    transaction.setPaymentStatus(PaymentStatus.PAID);
    transaction.setProfessional(order.getProfessional());
    transaction.setProcessingCost(new BigDecimal("0.99"));//Taxa de processamento do pagarme
    transaction.setPaymentMdrTax(new BigDecimal("4.49")); //Taxa MDR do pagarme
    transaction.setAppTaxPercentage(new BigDecimal(appTax));
    transaction.setAppTaxValue(calculateTaxValue(order.getTotalCost(), transaction.getAppTaxPercentage()));
    transaction.setProfessionalValue(calculateProfessionalValue(order.getTotalCost(), transaction.getAppTaxValue())); //Valor do professional

    return update(transaction);
  }


  @Override
  public Transaction update(Transaction transaction) {
    find(transaction.getId()).orElseThrow(() -> new ObjectNotFoundException("Transação não encontrada."));
    return transactionRepository.save(transaction);
  }

  @Override
  public Transaction giveCreditToCustomer(Order order) {
    //Pegando o Finance do Customer
    Finance userFinance = financeService.findByUser(order.getCustomer().getId());
    userFinance.setCustomerBalance(userFinance.getCustomerBalance().add(order.getTotalCost()));

    financeService.save(userFinance);
    //Pegando a transaction
    Transaction transaction = findByOrderId(order.getId()).orElseThrow(() -> new ObjectNotFoundException("Transaction not found."));
    //Setando o status da transaction para o Customer Balance
    transaction.setPaymentStatus(PaymentStatus.CUSTOMER_BALANCE);

    return update(transaction);
  }

  @Override
  public void refundPayment(Order order) {
    Transaction transaction = findByOrderId(order.getId()).orElseThrow(() -> new ObjectNotFoundException("Transaction not found."));
    //Caso o valor do estorno seja menor do que o valor da cobrança, colocar o valor para estorno no amount do cancelchargerequest
    var cancelCharge = CancelChargeRequest.builder().build();
    pagarmeService.cancelAndRefundChargeOrder(transaction.getPaymentTransactionId(), cancelCharge);
  }

  private OrderResponse holdPayment(Order order, BigDecimal creditCardValue) {
    // Finance para pegar o CustomerPaymentProfile
    Finance userFinance = financeService.findByUser(order.getCustomer().getId());

    // Build do request da transaction para o pagarme
    var orderRequest =
        OrderRequest.builder()
            .items(
                List.of(
                    OrderItemRequest.builder()
                        .code(order.getProduct().getId().toString())
                        .amount(convertBigDecimalToIntegerPagarme(creditCardValue))
                        .description("Product ID " + order.getProduct().getId())
                        .quantity(1)
                        .build()))
            .customerId(userFinance.getCustomerPaymentProfile())
            .payments(
                List.of(
                    PaymentRequest.builder()
                        .paymentMethod("credit_card")
                        .amount(convertBigDecimalToIntegerPagarme(creditCardValue))
                        .creditCard(
                            CreditCardPaymentRequest.builder()
                                .installments(1)
                                .statementDescriptor("PFIGHTERS")
                                .cardId(order.getPayment().getCard().getPaymentServiceId())
                                .operationType("auth_only")
                                .recurrence(false)
                                .build())
                        .build()))
            .code(order.getId().toString())
            .antifraudEnabled(true)
            .closed(true)
            .build();

//    log.info(orderRequest.toString());
    // Criando a transaçao no pagarme
    return pagarmeService.createOrder(orderRequest);
  }

  private Billing createPaymentBilling(Order order) {
    //Build do endereço para o Billing do request da transaçao do pagarme
    AddressBilling address = AddressBilling.builder()
        .street(order.getPayment().getCard().getStreet())
        .street_number(order.getPayment().getCard().getStreetNumber())
        .neighborhood(order.getPayment().getCard().getNeighborhood())
        .city(order.getPayment().getCard().getCity())
        .state(order.getPayment().getCard().getState())
        .zipcode(order.getPayment().getCard().getZipcode().replaceAll("-", ""))
        .country("br")
        .build();

    //Return da Build do Billing do request da transaçao do pagarme
    return Billing.builder()
        .name(order.getCustomer().getName() + " " + order.getCustomer().getSurname())
        .address(address)
        .build();
  }

  //PagarMe recebe os valores como Integer. ex: R$ 70,00 = 7000
  private Integer convertBigDecimalToIntegerPagarme(BigDecimal value) {
    return value.multiply(new BigDecimal("100")).intValue();
  }

  //PagarMe retorna os valores como Integer. ex: R$ 70,00 = 7000
  private BigDecimal convertIntegerToBigDecimal(Integer value) {
    return new BigDecimal(value / 100);
  }

  private BigDecimal calculateTaxValue(BigDecimal value, BigDecimal tax) {
    return value.multiply(tax.divide(new BigDecimal("100"), 2, RoundingMode.UP));
  }

  private BigDecimal calculateProfessionalValue(BigDecimal orderValue, BigDecimal appTaxValue) {
    return orderValue.subtract(appTaxValue);
  }

  private BigDecimal calculateProfit(Transaction transaction) {
    return transaction.getTotalPaid()
        .subtract(transaction.getPaymentTotalCost())
        .subtract(transaction.getProfessionalValue());
  }

  private Boolean checkTransactionIsNotAuthorizedAndNotPaid(String status) {
    return !status.equals(PaymentStatus.AUTHORIZED.getPaymentStatus().toLowerCase())
        && !status.equals(PaymentStatus.PAID.getPaymentStatus().toLowerCase());
  }
}


//  @Override
//  public Transaction create(Order order) {
//    Finance finance = financeService.findByUser(order.getCustomer().getId());
//
//    if (finance != null) {
//      BigDecimal customerBalanceValue = BigDecimal.ZERO;
//      BigDecimal creditCardValue = new BigDecimal(String.valueOf(order.getTotalCost()));
//
//      PaymentType paymentType = PaymentType.CREDIT_CARD;
//
//      /* Análise dos valores e tipo de pagamento */
//
//      //Verificando se o cliente tem crédito e abatendo o valor.
//      if (finance.getCustomerBalance().compareTo(BigDecimal.ZERO) > 0) {
//        //Verificando se o valor do crédito é maior que o valor da order
//        if (finance.getCustomerBalance().compareTo(order.getTotalCost()) >= 0) {
//          customerBalanceValue = customerBalanceValue.add(order.getTotalCost());
//          finance.setCustomerBalance(finance.getCustomerBalance().subtract(order.getTotalCost()));
//          paymentType = PaymentType.CUSTOMER_BALANCE;
//        } else {
//          customerBalanceValue = customerBalanceValue.add(finance.getCustomerBalance());
//          creditCardValue = order.getTotalCost().subtract(customerBalanceValue);
//          finance.setCustomerBalance(finance.getCustomerBalance().subtract(customerBalanceValue));
//          paymentType = PaymentType.CUSTOMER_BALANCE_AND_CREDIT_CARD;
//        }
//      } else {
//        creditCardValue = creditCardValue.add(order.getTotalCost());
//      }
//
//      /* Fim da Análise dos valores e tipo de pagamento */
//
//      /* #Pagamento pelo cartão# */
//      TransactionPagarme transactionPagarme = new TransactionPagarme();
//      boolean transactionHasError = false;
//      //Verificando se tem valor para ser pago no cartão de crédito e efetuando o pagamento.
//      if (creditCardValue.compareTo(BigDecimal.ZERO) > 0) {
//        try {
//          transactionPagarme = createTransactionPagarme(order, creditCardValue);
//        } catch (PaymentException e) {
//          log.error("Erro no pagamento da order: " + order.getId());
//          transactionPagarme.setStatus(PaymentStatus.ERROR.toString().toLowerCase());
//          transactionPagarme.setStatus_reason(PaymentStatusReason.INTERNAL_ERROR.toString().toLowerCase());
//          transactionPagarme.setRefuse_reason(PaymentStatusReason.INTERNAL_ERROR.toString().toLowerCase());
//        }
//        transactionHasError = transactionPagarme.getStatus().equals("error");
//      }
//      /* Fim do Pagamento pelo cartão */
//
//      /* Verificando status da transação e setando os valores na transaction */
//      PaymentStatus paymentStatus;
//      String paymentId = null;
//
//      //Cancelando a order se houver algum erro com o PagarMe, se a transação não estiver autorizada ou paga
//      // ou se os valores não forem iguais
//      if (transactionHasError || checkTransactionIsNotAuthorizedAndNotPaid(transactionPagarme.getStatus()) ||
//          transactionPagarme.getAuthorized_amount() < transactionPagarme.getAmount()) {
//
//        order.setStatus(Status.CANCELLED);
//        orderRepository.save(order);
//
//        boolean valuesNotEquals = false;
//
//        if (!transactionHasError) {
//          valuesNotEquals = transactionPagarme.getAuthorized_amount() < transactionPagarme.getAmount();
//          paymentId = String.valueOf(transactionPagarme.getId());
//        }
//
//        PaymentStatusReason refuseReason;
//        if(valuesNotEquals && !checkTransactionIsNotAuthorizedAndNotPaid(transactionPagarme.getStatus())) {
//          paymentStatus = PaymentStatus.ERROR;
//          refuseReason = PaymentStatusReason.PAYMENT_AUTHORIZED_AMOUNT_ERROR;
//        } else {
//          paymentStatus = PaymentStatus.valueOf(transactionPagarme.getStatus().toUpperCase());
//          refuseReason = transactionPagarme.getRefuse_reason() != null ?
//              PaymentStatusReason.valueOf(transactionPagarme.getRefuse_reason().toUpperCase()) : null;
//        }
//
//        Transaction transaction = Transaction.builder()
//            .paymentTransactionId(paymentId)
//            .paymentStatus(paymentStatus)
//            .refuseReason(refuseReason)
//            .paymentType(paymentType)
//            .acquirerName(!transactionHasError ? transactionPagarme.getAcquirer_name() : null)
//            .customerBalanceValue(customerBalanceValue)
//            .creditCardValue(creditCardValue)
//            .totalPaid(BigDecimal.ZERO)
//            .order(order)
//            .customer(order.getCustomer())
//            .card(order.getPayment().getCard())
//            .processingCost(BigDecimal.ZERO)
//            .paymentMdrTax(BigDecimal.ZERO)
//            .paymentMdrValue(BigDecimal.ZERO)
//            .paymentTotalCost(BigDecimal.ZERO)
//            .appProfit(BigDecimal.ZERO)
//            .appTaxPercentage(new BigDecimal(appTax))
//            .appTaxValue(BigDecimal.ZERO)
//            .professionalValue(BigDecimal.ZERO)
//            .billingOrderDate(order.getDate())
//            .orderIsDone(false)
//            .professionalIsPaid(false)
//            .build();
//
//        return save(transaction);
//      } else {
//
//        //Setando o status de pagamento pelo tipo de pagamento
//        if (paymentType.equals(PaymentType.CREDIT_CARD) || paymentType.equals(PaymentType.CUSTOMER_BALANCE_AND_CREDIT_CARD)) {
//          paymentStatus = PaymentStatus.valueOf(transactionPagarme.getStatus().toUpperCase());
//          paymentId = String.valueOf(transactionPagarme.getId());
//        } else {
//          paymentStatus = PaymentStatus.PAID;
//          paymentId = null;
//        }
//
//        Transaction transaction = Transaction.builder()
//            .paymentStatus(paymentStatus)
//            .paymentType(paymentType)
//            .paymentTransactionId(paymentId)
//            .customerBalanceValue(customerBalanceValue)
//            .creditCardValue(creditCardValue)
//            .totalPaid(order.getTotalCost())
//            .order(order)
//            .acquirerName(transactionPagarme.getAcquirer_name())
//            .customer(order.getCustomer())
//            .card(!paymentType.equals(PaymentType.CUSTOMER_BALANCE) ? order.getPayment().getCard() : null)
//            .processingCost(convertIntegerToBigDecimal(transactionPagarme.getCost()))
//            .paymentMdrTax(BigDecimal.ZERO)
//            .paymentMdrValue(BigDecimal.ZERO)
//            .paymentTotalCost(convertIntegerToBigDecimal(transactionPagarme.getCost()))
//            .appTaxPercentage(new BigDecimal(appTax))
//            .appTaxValue(BigDecimal.ZERO)
//            .professionalValue(BigDecimal.ZERO)
//            .appProfit(BigDecimal.ZERO)
//            .billingOrderDate(order.getDate())
//            .orderIsDone(false)
//            .professionalIsPaid(false)
//            .build();
//
//        financeService.save(finance);
//
//        return save(transaction);
//      }
//      /* Verificando status da transação e setando os valores na transaction */
//    } else {
//      order.setStatus(Status.CANCELLED);
//      orderRepository.save(order);
//      throw new ObjectNotFoundException("Payment failed");
//    }
//  }