package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;

import java.util.Optional;
import java.util.UUID;

public interface TransactionService {

  Optional<Transaction> find(Long id);

  Optional<Transaction> findByOrderId(UUID id);

  Transaction save(Transaction transaction);

  Transaction doHoldPayment(Order order);

  Transaction doPayment(Order order);

  Transaction update(Transaction transaction);

  Transaction giveCreditToCustomer(Order order);

  void refundPayment(Order order);

}
