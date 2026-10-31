package br.com.personalfighters.model.pagarme.request;

import br.com.personalfighters.model.pagarme.Billing;
import br.com.personalfighters.model.pagarme.CustomerPagarme;
import br.com.personalfighters.model.pagarme.Item;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Data
@ToString
public class TransactionBody {

  private Integer amount;

  @JsonProperty("card_id")
  private String cardId;

  private CustomerTransactionRequest customer;

  private Billing billing;

  private List<Item> items = new ArrayList<>();

  @JsonProperty("payment_method")
  private String paymentMethod;

//    @JsonProperty("postback_url")
//    private String postbackUrl;

  private String capture;

  private boolean async;

  public TransactionBody(TransactionRequest transactionRequest, CustomerPagarme customer) {
    this.amount = transactionRequest.getAmount();
    this.cardId = transactionRequest.getCardId();
    this.customer = new CustomerTransactionRequest(customer);
    this.billing = transactionRequest.getBilling();

    this.paymentMethod = "credit_card";
//        this.postbackUrl = null;
    this.capture = transactionRequest.getCapture();
    this.async = false;
  }
}
