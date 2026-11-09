package br.com.personalfighters.service;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.Transaction;

public interface AsaasService {

  Transaction createPixCharge(Order order);

  void releasePaymentToProfessional(Order order);

  String createOrGetCustomer(br.com.personalfighters.entity.User user);

  /** Sem chave do Asaas configurada: PIX simulado para testes. */
  boolean isTestMode();

  /** Devolve o PIX ao cliente no Asaas (value null = valor total). Retorna null se deu certo ou a mensagem de erro. */
  String refundPix(String asaasPaymentId, java.math.BigDecimal value);

  /** Cancela uma cobrança PIX ainda não paga (ninguém consegue pagar depois). */
  boolean cancelCharge(String asaasPaymentId);

  /** Cancela as cobranças ainda não pagas do pedido (ex.: o valor mudou). */
  void cancelOpenCharges(br.com.personalfighters.entity.Order order);

  /** Cobrança PIX avulsa (orçamento extra). Retorna {id, payload, encodedImage}. */
  java.util.Map<String, String> createExtraCharge(br.com.personalfighters.entity.User customer, java.math.BigDecimal value, String description);

  /** Transferência PIX avulsa para o profissional (orçamento extra, taxa de cancelamento). */
  boolean transferToProfessional(br.com.personalfighters.entity.User professional, java.math.BigDecimal value, String description);

  /** PIX para o CPF ou o CNPJ conferido do próprio profissional (a chave é o documento). */
  boolean transferPixToOwnDoc(br.com.personalfighters.entity.User professional, String docDigits, java.math.BigDecimal value, String description);

  /** Transferência para a conta bancária (agência e conta) do próprio profissional. */
  boolean transferToBank(br.com.personalfighters.entity.User professional, java.util.Map<String, Object> bank, java.math.BigDecimal value, String description);

}
