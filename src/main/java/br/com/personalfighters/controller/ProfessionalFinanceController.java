package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.model.IncomeDTO;
import br.com.personalfighters.service.ProfessionalFinanceService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/professionalfinance")
public class ProfessionalFinanceController {

  private final ProfessionalFinanceService professionalFinanceService;

  public ProfessionalFinanceController(ProfessionalFinanceService professionalFinanceService) {
    this.professionalFinanceService = professionalFinanceService;
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object find(@PathVariable Long id) {
    return professionalFinanceService.find(id);
  }

  @GetMapping("/professional/{id}")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findByProfessional(@PathVariable Long id) {
    return professionalFinanceService.findByProfessional(id);
  }

  @PostMapping("/professional/income")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findByProfessionalAndDate(@RequestBody IncomeDTO data) {
    return professionalFinanceService.findProfessionalIncomeByDate(data);
  }

  @PostMapping("/transactions/income")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findTransactionsByProfessionalAndDate(@RequestBody IncomeDTO data) {
    return professionalFinanceService.findProfessionalTransactionsByDate(data);
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object update(@RequestBody Finance finance) {
    return professionalFinanceService.update(finance);
  }


}
