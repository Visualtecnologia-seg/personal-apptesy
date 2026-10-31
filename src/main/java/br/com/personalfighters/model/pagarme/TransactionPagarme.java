package br.com.personalfighters.model.pagarme;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class TransactionPagarme {

  @JsonIgnore
  private String object;

  private String status;

  private String refuse_reason;

  private String status_reason;

  private String acquirer_response_code;

  private String acquirer_name;

  private String acquirer_id;

  private String authorization_code;

  private String soft_descriptor;

  private String tid;

  private String nsu;

  private String date_created;

  private String date_updated;

  private Integer amount;

  private Integer authorized_amount;

  private Integer paid_amount;

  private Integer refunded_amount;

  private Integer installments;

  private Integer id;

  private Integer cost;

  private String card_holder_name;

  private String card_last_digits;

  private String card_first_digits;

  private String card_brand;

  private String card_pin_mode;

  private String postback_url;

  private String payment_method;

  private String capture_method;

  private String antifraud_score;

  private String boleto_url;

  private String boleto_barcode;

  private String boleto_expiration_date;

  private String referer;

  private String ip;

  private Integer subscription_id;

  private String phone;

  private AddressPagarme address;

  private CustomerPagarme customer;

  private Billing billing;

  private Shipping shipping;

  private List<Item> items;

  private Card card;

}
