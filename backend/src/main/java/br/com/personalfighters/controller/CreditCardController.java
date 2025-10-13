package br.com.personalfighters.controller;

import br.com.personalfighters.model.pagarme.request.CardUnsafeRequest;
import br.com.personalfighters.service.CreditCardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RequiredArgsConstructor
@RestController
@Api(tags = {"Credit Cards"})
@RequestMapping("/credit-cards")
public class CreditCardController {

  private final CreditCardService creditCardService;

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna Cartão pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public Object findById(@PathVariable Long id) {
    return creditCardService.findById(id);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna Cartão pelo ID do User")
  @ResponseStatus(HttpStatus.OK)
  public Object findAllByUser(@PathVariable Long id, Pageable pageable) {
    return creditCardService.findAllByUser(id, pageable);
  }

  @PostMapping("/users/{id}")
  @ApiOperation(value = "Salva um Cartão")
  public Object save(@PathVariable Long id, @RequestBody CardUnsafeRequest creditCard) {
    return creditCardService.save(id, creditCard);
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Deleta info pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) throws Exception {
    creditCardService.delete(id);
  }

}