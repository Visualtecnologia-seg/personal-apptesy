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
    Long id = ((Number) rows.get(0).get("id")).longValue();

    Long open = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE (customer_id = ? OR professional_id = ?) AND status NOT IN ('DONE','REVIEWED','CANCELLED','BLOCKED')", Long.class, id, id);
    if (open != null && open > 0) return bad("A conta tem " + open + " pedido(s) em andamento. Cancele antes de apagar.");
    try {
      BigDecimal avail = walletService.available(id);
      if (avail != null && avail.compareTo(BigDecimal.ONE) >= 0) return bad("A conta ainda tem R$ " + avail.setScale(2, java.math.RoundingMode.DOWN).toPlainString().replace('.', ',') + " na carteira. Faça o saque antes.");
    } catch (Exception ignored) { }

    String tag = "excluido-" + id;
    Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
    jdbc.update("UPDATE user_ SET name = 'Conta excluída', surname = NULL, email = ?, username = ?, cpf = ?, cnpj = NULL, cnpj_name = NULL, cnpj_verified_at = NULL, " +
            "phone_number = NULL, phone_verified = FALSE, birthday = NULL, avatar_url = NULL, latitude = NULL, longitude = NULL, full_address = NULL, " +
            "pix_key = NULL, pix_key_type = NULL, document_url = NULL, doc_front_url = NULL, doc_back_url = NULL, doc_selfie_url = NULL, " +
            "google_sub = NULL, apple_sub = NULL, email_marketing = FALSE, password = ?, active = FALSE, deleted_at = ? WHERE id = ?",
        tag + "@excluido.opus", tag, ("x" + id), passwordEncoder.encode(UUID.randomUUID().toString()), now, id);
    int removed = 0;
    for (String sql : new String[]{
        "DELETE FROM identity_verification_ WHERE user_id = ?",
        "DELETE FROM user_device_ WHERE user_id = ?",
        "DELETE FROM favorite_ WHERE user_id = ? OR professional_id = ?",
        "DELETE FROM follow_ WHERE user_id = ? OR professional_id = ?",
        "DELETE FROM post_video_ WHERE post_id IN (SELECT id FROM post_ WHERE professional_id = ?)",
        "DELETE FROM post_ WHERE professional_id = ?",
        "DELETE FROM support_state_ WHERE user_id = ?",
        "DELETE FROM data_export_token_ WHERE user_id = ?",
        "DELETE FROM bank_account_ WHERE user_id = ?",
        "DELETE FROM bankdata_ WHERE professional_id = ?",
        "DELETE FROM address_ WHERE user_id = ?",
        "DELETE FROM credit_card_ WHERE user_id = ?",
        "DELETE FROM photo_ WHERE user_id = ?",
        "DELETE FROM user_records_ WHERE user_id = ?",
        "DELETE FROM password_reset_ WHERE user_id = ?",
        "DELETE FROM email_log_ WHERE user_id = ?",
        "DELETE FROM product__professional WHERE professional_id = ?"}) {
      try { removed += sql.chars().filter(c -> c == '?').count() == 2 ? jdbc.update(sql, id, id) : jdbc.update(sql, id); }
      catch (Exception e) { log.debug("Exclusão pelo administrador: {} ({})", sql, e.getMessage()); }
    }
    try { jdbc.update("UPDATE professional_ SET is_online = FALSE WHERE id = (SELECT professional_id FROM user_ WHERE id = ?)", id); } catch (Exception ignored) { }
    authenticationService.revokeAll(id);
    security.event(id, "ACCOUNT_DELETED", "Conta apagada pelo administrador a pedido da pessoa");
    log.info("LGPD: conta {} apagada pelo administrador", id);
    return ResponseEntity.ok(Map.of("ok", true, "id", id, "removed", removed));
  }
}
