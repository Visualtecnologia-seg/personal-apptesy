package br.com.personalfighters.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regra de preço do OPUS: a taxa de 5% é embutida no valor que o cliente paga.
 * O profissional recebe o valor cheio do serviço.
 */
public final class Pricing {

  public static volatile BigDecimal FEE_RATE = new BigDecimal("0.05");

  private Pricing() {}

  /** Valor que o cliente paga = serviço + 5%. */
  public static BigDecimal clientTotal(BigDecimal serviceValue) {
    if (serviceValue == null) return null;
    return serviceValue.multiply(BigDecimal.ONE.add(FEE_RATE)).setScale(2, RoundingMode.HALF_UP);
  }

  /** Valor do serviço a partir do total do cliente (para pedidos antigos). */
  public static BigDecimal serviceFromTotal(BigDecimal clientTotal) {
    if (clientTotal == null) return null;
    return clientTotal.divide(BigDecimal.ONE.add(FEE_RATE), 2, RoundingMode.HALF_UP);
  }
}
