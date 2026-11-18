package br.com.personalfighters.service;

import br.com.personalfighters.service.impl.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** Apaga de vez os dados pessoais de uma conta (LGPD). Pedidos e pagamentos ficam sem os dados da pessoa. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountEraseService {

  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationService authenticationService;
  private final SecurityService security;
  private final WalletService walletService;

  /** Quantos registros a última exclusão removeu (só para o painel). */
  public volatile int lastRemoved;

  /** Mesmo apagamento do botão do administrador. Devolve null se apagou, ou o motivo de não poder apagar. */
  public String erase(Long id, String why) {

    Long open = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE (customer_id = ? OR professional_id = ?) AND status NOT IN ('DONE','REVIEWED','CANCELLED','BLOCKED')", Long.class, id, id);
    if (open != null && open > 0) return "A conta tem " + open + " pedido(s) em andamento. Termine ou cancele antes de apagar.";
    try {
      BigDecimal avail = walletService.available(id);
      if (avail != null && avail.compareTo(BigDecimal.ONE) >= 0) return "A conta ainda tem R$ " + avail.setScale(2, java.math.RoundingMode.DOWN).toPlainString().replace('.', ',') + " para sacar. Faça o saque antes de apagar.";
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
      catch (Exception e) { log.debug("Exclusão: {} ({})", sql, e.getMessage()); }
    }
    try { jdbc.update("UPDATE professional_ SET is_online = FALSE WHERE id = (SELECT professional_id FROM user_ WHERE id = ?)", id); } catch (Exception ignored) { }
    authenticationService.revokeAll(id);
    security.event(id, "ACCOUNT_DELETED", why);
    log.info("LGPD: conta {} apagada ({})", id, why);
    lastRemoved = removed;
    return null;
  }
}
