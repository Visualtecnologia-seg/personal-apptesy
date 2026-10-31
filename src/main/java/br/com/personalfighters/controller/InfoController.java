package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Info;
import br.com.personalfighters.service.InfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RequiredArgsConstructor
@RestController
@Api(tags = {"Info"})
@RequestMapping("/infos")
public class InfoController {

  private final InfoService infoService;

  @GetMapping
  @ApiOperation(value = "Retorna lista de infos")
  @ResponseStatus(HttpStatus.OK)
  public Object findAll(Info info, Pageable pageable) {
    return infoService.findAll(info, pageable);
  }

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna info pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public Object findById(@PathVariable Long id) {
    return infoService.findById(id);
  }

  @PostMapping
  @ApiOperation(value = "Salva info")
  public Object save(@RequestBody Info info) {
    return infoService.save(info);
  }

  @PutMapping
  @ApiOperation(value = "Atualiza info")
  public Object update(@RequestBody Info info) {
    return infoService.save(info);
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Deleta info pelo ID")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) throws Exception {
    infoService.delete(id);
  }

}