package br.com.personalfighters.controller;

import br.com.personalfighters.entity.BankData;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.BankDataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Customer Finance"})
@RequestMapping("/bank")
public class BankDataController {

  private final BankDataService bankDataService;

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna os dados bancários do usuário")
  @ResponseStatus(HttpStatus.CREATED)
  public BankData save(@PathVariable Long id) {
    return bankDataService.findByProfessionalId(id);
  }

  @PostMapping
  @ApiOperation(value = "Salva os dados bancários")
  @ResponseStatus(HttpStatus.CREATED)
  public void save(@RequestBody BankData bankData) {
    bankDataService.save(bankData);
  }

}
