package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;

public interface AsaasService {

  Transaction createPixCharge(Order order);

  void releasePaymentToProfessional(Order order);

  String createOrGetCustomer(br.com.personalfighters.entity.User user);

}
