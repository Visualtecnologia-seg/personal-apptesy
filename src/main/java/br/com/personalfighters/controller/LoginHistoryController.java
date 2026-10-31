package br.com.personalfighters.controller;

import br.com.personalfighters.entity.LoginHistory;
import br.com.personalfighters.repository.LoginHistoryRepository;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"LoginHistory"})
@RequestMapping("/auth/history")
public class LoginHistoryController {

  private final LoginHistoryRepository loginHistoryRepository;

  @GetMapping("/{userId}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna as últimas sessões do utilizador.")
  public List<LoginHistory> getHistory(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "10") int limit) {
    return loginHistoryRepository.findByUserIdOrderByCreatedAtDesc(
        userId, PageRequest.of(0, Math.min(limit, 50)));
  }
}
