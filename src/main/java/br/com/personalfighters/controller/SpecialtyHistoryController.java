package br.com.personalfighters.controller;

import br.com.personalfighters.entity.SpecialtyHistory;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.SpecialtyHistoryRepository;
import br.com.personalfighters.service.UserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"SpecialtyHistory"})
@RequestMapping("/specialty-history")
public class SpecialtyHistoryController {

  private final SpecialtyHistoryRepository specialtyHistoryRepository;
  private final UserService userService;

  @GetMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o histórico de especialidades do profissional.")
  public List<SpecialtyHistory> getHistory(@PathVariable Long id) {
    User user = userService.findById(id);
    return specialtyHistoryRepository.findByUserOrderByChangedAtDesc(user);
  }

  @PostMapping("/users/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Regista uma alteração de especialidades.")
  public SpecialtyHistory save(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
    User user = userService.findById(id);
    SpecialtyHistory history = SpecialtyHistory.builder()
        .user(user)
        .specialties(body.getOrDefault("specialties", ""))
        .changedAt(LocalDateTime.now())
        .build();
    return specialtyHistoryRepository.save(history);
  }
}
