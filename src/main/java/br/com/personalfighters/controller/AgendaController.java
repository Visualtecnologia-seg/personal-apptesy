package br.com.personalfighters.controller;

import br.com.personalfighters.entity.ProfessionalAgenda;
import br.com.personalfighters.service.ProductAgendaService;
import br.com.personalfighters.service.ProfessionalAgendaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Agendas"})
@RequestMapping("/agendas")
public class AgendaController {

  private final ProfessionalAgendaService professionalAgendaService;
  private final ProductAgendaService productAgendaService;

  @PutMapping
  @ApiOperation(value = "Atualiza agenda.")
  @ResponseStatus(HttpStatus.OK)
  public Object update(@RequestBody ProfessionalAgenda professionalAgenda) {
    return professionalAgendaService.save(professionalAgenda);
  }

  @GetMapping("/user/{id}")
  @ApiOperation(value = "Retorna lista de agendas do professional.")
  @ResponseStatus(HttpStatus.OK)
  public Object findByUserId(@PathVariable Long id) {
    return professionalAgendaService.findByUserId(id);
  }

  @GetMapping("/user/{id}/date/{date}")
  @ApiOperation(value = "Retorna agenda pelo ID do professional e pela data.")
  @ResponseStatus(HttpStatus.OK)
  public Object findByUserIdAndDate(@PathVariable Long id, @PathVariable String date) {
    return professionalAgendaService.findByUserIdAndDate(id, date);
  }

  @GetMapping("/available/product/{id}")
  @ApiOperation(value = "Retorna agenda disponível pelo ID do produto.")
  @ResponseStatus(HttpStatus.OK)
  public Object findByProductId(@PathVariable("id") Long id) {
    return productAgendaService.findByProductId(id);
  }

}
