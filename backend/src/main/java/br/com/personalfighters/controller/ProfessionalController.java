package br.com.personalfighters.controller;

import br.com.personalfighters.service.ProfessionalService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Professionals"})
@RequestMapping("/professionals")
public class ProfessionalController {

  private final ProfessionalService professionalService;

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Inativa um profissional pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public void setActive(@PathVariable Long id) {
    professionalService.setActive(id);
  }

}
