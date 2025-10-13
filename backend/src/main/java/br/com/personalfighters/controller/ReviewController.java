package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Review;
import br.com.personalfighters.service.ReviewService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Reviews"})
@RequestMapping("/reviews")
public class ReviewController {

  private final ReviewService reviewService;

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna review pelo ID.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object find(@PathVariable Long id) {
    return reviewService.find(id);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna a lista de reviews do professional.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object findByUser(@PathVariable Long id, Pageable pageable) {
    return reviewService.findByUser(id, pageable);
  }

  @PostMapping
  @ApiOperation(value = "Salva a review.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FirstLevel.class)
  public Object save(@RequestBody Review review) {
    return reviewService.save(review);
  }

  @DeleteMapping("/{id}")
  @ApiOperation(value = "Deleta review pelo ID.")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) {
    reviewService.delete(id);
  }
}
