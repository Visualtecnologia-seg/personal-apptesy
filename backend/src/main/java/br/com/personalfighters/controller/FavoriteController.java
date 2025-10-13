package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Favorite;
import br.com.personalfighters.service.FavoriteService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Favorites"})
@RequestMapping("/favorites")
public class FavoriteController {

  private final FavoriteService favoriteService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Page<Favorite> findAll(@RequestBody Favorite favorite, Pageable pageable) {
    return favoriteService.findAll(favorite, pageable);
  }

  @GetMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Object findAllByUser(@PathVariable Long id, Pageable pageable) {
    return favoriteService.findAllByUser(id, pageable);
  }

  @PostMapping("/professionals/{ref}")
  @ResponseStatus(HttpStatus.OK)
  public void save(@PathVariable String ref, @RequestBody Favorite favorite) {
    favoriteService.save(favorite, ref);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public void delete(@PathVariable Long id) throws Exception {
    favoriteService.delete(id);
  }

}
