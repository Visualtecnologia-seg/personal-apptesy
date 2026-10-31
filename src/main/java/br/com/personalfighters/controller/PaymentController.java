package br.com.personalfighters.controller;

import br.com.personalfighters.model.pagarme.request.CaptureTransactionRequest;
import br.com.personalfighters.service.PagarmeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@Api(tags = {"Payments"})
@RequestMapping("/payments")
public class PaymentController {

  private final PagarmeService pagarmeService;

  public PaymentController(PagarmeService pagarmeService) {
    this.pagarmeService = pagarmeService;
  }

  //Verificar necessidade de utilizar no dashboard
  @PostMapping("/transactions/{transactionId}/capture")
  @ApiOperation(
      value = "Captura uma transação"
  )
  @ResponseStatus(HttpStatus.OK)
  public Object captureTransaction(
      @PathVariable Integer transactionId,
      @RequestBody CaptureTransactionRequest captureTransactionRequest
  ) {
    return pagarmeService.captureTransaction(transactionId, captureTransactionRequest);
  }

  @PostMapping("/transactions/{transactionId}/refund")
  @ApiOperation(
      value = "Estorna o valor total da transação para o cartão"
  )
  @ResponseStatus(HttpStatus.OK)
  public Object refundTransaction(@PathVariable Integer transactionId) {
    return pagarmeService.refundTransaction(transactionId);
  }

  //TODO verificar necessidade de estornar o valor parcialmente
  //Possíveis situações: Taxa de cancelamento, cliente não aparecer, e etc...
}
