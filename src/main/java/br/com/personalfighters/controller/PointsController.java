package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Points;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.PointsRepository;
import br.com.personalfighters.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Points"})
@RequestMapping("/points")
public class PointsController {

  private final PointsRepository pointsRepository;
  private final UserService userService;

  @GetMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o histórico de pontos do usuário.")
  public List<Points> getHistory(@PathVariable Long id) {
    User user = userService.findById(id);
    return pointsRepository.findByUserOrderByCreatedAtDesc(user);
  }

  @GetMapping("/users/{id}/balance")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o saldo de pontos do usuário.")
  public Map<String, Object> getBalance(@PathVariable Long id) {
    User user = userService.findById(id);
    Integer total = pointsRepository.sumAmountByUser(user);
    return Map.of("userId", id, "balance", total == null ? 0 : total);
  }
}
