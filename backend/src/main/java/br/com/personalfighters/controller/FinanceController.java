package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@Api(tags = {"Customer Finance"})
@RequestMapping("/finances")
public class FinanceController {

  private final FinanceService financeService;

  public FinanceController(FinanceService financeService) {
    this.financeService = financeService;
  }

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna a finance pelo ID")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object find(@PathVariable Long id) {
    return financeService.find(id);
  }

  @GetMapping
  @ApiOperation(value = "Retorna lista de finance")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findAll(Finance finance, Pageable pageRequest) {
    return financeService.findAll(finance, pageRequest);
  }

  @GetMapping("/data")
  @ApiOperation(value = "Retorna lista de finance")
  @ResponseStatus(HttpStatus.OK)
  public Object findAllFinanceData(Pageable pageRequest) {
    return financeService.findAllFinanceData(pageRequest);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna a finance pelo ID do User")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findByUser(@PathVariable Long id) {
    return financeService.findByUser(id);
  }

  @PutMapping("/{id}/pay")
  @ApiOperation(value = "Atualiza a finance")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public void payUser(@PathVariable Long id) {
    financeService.payUser(id);
  }

  @PutMapping
  @ApiOperation(value = "Atualiza a finance")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object update(@RequestBody Finance finance) {
    return financeService.save(finance);
  }

}
