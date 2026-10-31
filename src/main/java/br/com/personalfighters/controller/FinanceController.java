package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.service.PayoutCodeService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Customer Finance"})
@RequestMapping("/finances")
public class FinanceController {

  private final FinanceService financeService;
  private final EmailService emailService;
  private final PayoutCodeService payoutCodeService;

  @GetMapping("/{id}")
  @ApiOperation(value = "Retorna a finance pelo ID")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object find(@PathVariable Long id) {
    return financeService.find(id);
  }

  @GetMapping
  @ApiOperation(value = "Retorna lista de finance")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findAll(Finance finance, Pageable pageRequest) {
    return financeService.findAll(finance, pageRequest);
  }

  @GetMapping("/data")
  @ApiOperation(value = "Retorna lista de finance")
  @ResponseStatus(HttpStatus.OK)
  public Object findAllFinanceData(Pageable pageRequest) {
    return financeService.findAllFinanceData(pageRequest);
  }

  @GetMapping("/users/{id}")
  @ApiOperation(value = "Retorna a finance pelo ID do User")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object findByUser(@PathVariable Long id) {
    return financeService.findByUser(id);
  }

  @GetMapping("/summary")
  @ApiOperation(value = "Retorna resumo financeiro da plataforma")
  @ResponseStatus(HttpStatus.OK)
  public Object getSummary() {
    return financeService.getSummary();
  }

  @PutMapping
  @ApiOperation(value = "Atualiza a finance")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public Object update(@RequestBody Finance finance) {
    return financeService.save(finance);
  }

  // ── Payout com código de confirmação ────────────────────────────────────────

  @PostMapping("/{id}/payout-code")
  @ApiOperation(value = "Envia código de confirmação de saque por e-mail ao utilizador da finance.")
  @ResponseStatus(HttpStatus.OK)
  public ResponseEntity<?> sendPayoutCode(@PathVariable Long id) {
    try {
      Object findResult = financeService.find(id);
      Finance finance = null;
      if (findResult instanceof java.util.Optional) {
        Object inner = ((java.util.Optional<?>) findResult).orElse(null);
        if (inner instanceof Finance) finance = (Finance) inner;
      } else if (findResult instanceof Finance) {
        finance = (Finance) findResult;
      }
      if (finance == null || finance.getUser() == null) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", "Finance ou utilizador não encontrado."));
      }
      String code = payoutCodeService.generateCode(id);
      emailService.sendPayoutCode(
          finance.getUser().getEmail(),
          finance.getUser().getName(),
          code);
      return ResponseEntity.ok(Map.of(
          "message", "Código enviado para " + maskEmail(finance.getUser().getEmail()),
          "expiresInMinutes", 15
      ));
    } catch (Exception e) {
      log.error("Erro ao enviar código de saque: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Não foi possível enviar o código."));
    }
  }

  @PutMapping("/{id}/pay")
  @ApiOperation(value = "Processa o pagamento da finance. Admins não precisam de código; utilizadores comuns precisam.")
  @ResponseStatus(HttpStatus.OK)
  @JsonView(Views.FinanceView.FirstLevel.class)
  public void payUser(@PathVariable Long id,
                      @RequestParam(required = false) String code) {
    boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
        .getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

    if (!isAdmin) {
      if (code == null || code.isBlank()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Código de confirmação obrigatório para processar o saque.");
      }
      if (!payoutCodeService.validateAndConsume(id, code)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Código de confirmação inválido ou expirado. Solicite um novo código.");
      }
    }
    financeService.payUser(id);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) return email;
    String[] parts = email.split("@");
    String local = parts[0];
    String shown = local.length() <= 3
        ? local.charAt(0) + "***"
        : local.substring(0, 3) + "***";
    return shown + "@" + parts[1];
  }
}
