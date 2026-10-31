package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Product;
import br.com.personalfighters.service.ProductService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Products"})
@RequestMapping("/products")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @ApiOperation(value = "Retorna lista de produtos.")
  @ResponseStatus(HttpStatus.OK)
  public Object findAll(Product product, Pageable pageable) {
    return productService.findAll(product, pageable);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna lista de produtos.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView({Views.Product.Basic.class})
  public List<Product> findProductByProfessional(@PathVariable Long id) {
    return productService.findProductByProfessional(id);
  }

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna produto pelo ID")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.Product.Basic.class)
  public Object findById(@PathVariable Long id) {
    return productService.findById(id);
  }

  @PostMapping
  @ApiOperation(value = "Salva produto")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.Product.Basic.class)
  public Object save(@RequestBody Product product) {
    return productService.save(product);
  }

  @PostMapping("/users/{id}")
  @ApiOperation(value = "Salva produto")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.Product.Basic.class)
  public void saveAll(@RequestBody List<Product> products, @PathVariable Long id) {
    productService.saveUserOnProducts(products, id);
  }

  @PutMapping
  @ApiOperation(value = "Atualiza produto")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.Product.Basic.class)
  public Object update(@RequestBody Product product) {
    return productService.save(product);
  }

  @PutMapping("/{id}")
  @ApiOperation(value = "Ativa ou inativa produto pelo ID")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.Product.Basic.class)
  public void setActive(@PathVariable Long id) {
    productService.setActive(id);
  }
}
