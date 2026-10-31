package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Category;
import br.com.personalfighters.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Categories"})
@RequestMapping("/categories")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @ApiOperation(value = "Retorna a lista de categorias")
  @ResponseStatus(HttpStatus.OK)
  public Object findAll(Category category, Pageable pageable) {
    return categoryService.findAll(category, pageable);
  }

  @PostMapping
  @ApiOperation(value = "Salva categoria")
  @ResponseStatus(HttpStatus.OK)
  public Object save(@RequestBody Category category) {
    return categoryService.save(category);
  }

  @PutMapping
  @ApiOperation(value = "Atualiza categoria")
  @ResponseStatus(HttpStatus.OK)
  public Object update(@RequestBody Category category) {
    return categoryService.save(category);
  }

}
