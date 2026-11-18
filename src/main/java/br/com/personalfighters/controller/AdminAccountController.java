package br.com.personalfighters.controller;

import br.com.personalfighters.service.SecurityService;
import br.com.personalfighters.service.WalletService;
import br.com.personalfighters.service.impl.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Administrador: apagar de vez a conta de alguém (a pedido da pessoa).
 * Os dados pessoais somem e o e-mail e o CPF ficam livres para um novo cadastro.
 * Pedidos e pagamentos continuam guardados, sem os dados da pessoa (obrigação fiscal).
 */
@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminAccountController {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationService authenticationService;
  private final SecurityService security;
  private final WalletService walletService;
  private final br.com.personalfighters.service.AccountEraseService eraser;

  private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("message", m)); }

  /** Mostra o que existe antes de apagar (para conferir). */
  @GetMapping("/erase-preview")
  public ResponseEntity<?> preview(@RequestParam String email) {
    List<Map<String, Object>> u = jdbc.queryForList(
        "SELECT id, name, email, username, deleted_at, (SELECT COUNT(*) FROM order_ o WHERE o.customer_id = user_.id OR o.professional_id = user_.id) AS orders " +
            "FROM user_ WHERE LOWER(email) = LOWER(?) AND username NOT LIKE 'painel:%'", email.trim());
    return ResponseEntity.ok(Map.of("found", u.size(), "users", u));
  }

  @PostMapping("/erase")
  public ResponseEntity<?> erase(@RequestBody Map<String, Object> b) {
    String email = Objects.toString(b.get("email"), "").trim();
    if (!"APAGAR".equals(b.get("confirm"))) return bad("Confirmação faltando.");
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM user_ WHERE LOWER(email) = LOWER(?) AND username NOT LIKE 'painel:%' AND deleted_at IS NULL", email);
    if (rows.isEmpty()) return bad("Nenhuma conta ativa com esse e-mail.");
    if (rows.size() > 1) return bad("Mais de uma conta com esse e-mail. Fale com o suporte técnico.");
    return eraseById(((Number) rows.get(0).get("id")).longValue());
  }

  /** Botão "Apagar conta de vez" na ficha da pessoa no painel. */
  @PostMapping("/{id}/erase")
  public ResponseEntity<?> eraseOne(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    if (!"APAGAR".equals(b.get("confirm"))) return bad("Digite APAGAR para confirmar.");
    Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE id = ? AND username NOT LIKE 'painel:%' AND deleted_at IS NULL", Integer.class, id);
    if (ok == null || ok == 0) return bad("Conta não encontrada ou já apagada.");
    return eraseById(id);
  }

  private ResponseEntity<?> eraseById(Long id) {
    String err = eraser.erase(id, "Conta apagada pelo administrador a pedido da pessoa");
    if (err != null) return bad(err);
    return ResponseEntity.ok(Map.of("ok", true, "id", id, "removed", eraser.lastRemoved));
  }
}
