package br.com.personalfighters.controller;

import br.com.personalfighters.service.CustomerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Customers"})
@RequestMapping("/customers")
public class CustomerController {

  private final CustomerService customerService;

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Inativa um customer pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public void setActive(@PathVariable Long id) {
    customerService.setActive(id);
  }

}
