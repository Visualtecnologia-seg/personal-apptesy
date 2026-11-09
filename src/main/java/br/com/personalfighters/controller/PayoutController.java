package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.PayoutService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

import static br.com.personalfighters.controller.SocialController.bad;

/** CNPJ (MEI) e conta bancária para receber. Sempre da própria conta. */
@RestController
@RequiredArgsConstructor
public class PayoutController {

  private final UserService userService;
  private final PayoutService payoutService;

  @GetMapping("/payout")
  public ResponseEntity<?> get() {
    return ResponseEntity.ok(payoutService.summary(userService.findAuthUser()));
  }

  @PostMapping("/payout/cnpj")
  public ResponseEntity<?> cnpj(@RequestBody Map<String, Object> body) {
    try {
      return ResponseEntity.ok(payoutService.verifyCnpj(userService.findAuthUser(), Objects.toString(body.get("cnpj"), "")));
    } catch (IllegalArgumentException e) {
      return bad(e.getMessage());
    }
  }

  @DeleteMapping("/payout/cnpj")
  public ResponseEntity<?> removeCnpj() {
    payoutService.removeCnpj(userService.findAuthUser());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @PutMapping("/payout/bank")
  public ResponseEntity<?> bank(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    try {
      return ResponseEntity.ok(payoutService.saveBank(me, body));
    } catch (IllegalArgumentException e) {
      return bad(e.getMessage());
    }
  }

  @DeleteMapping("/payout/bank")
  public ResponseEntity<?> removeBank() {
    payoutService.removeBank(userService.findAuthUser());
    return ResponseEntity.ok(Map.of("ok", true));
  }
}
