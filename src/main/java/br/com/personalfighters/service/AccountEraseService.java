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
      // Quem deve ao OPUS (pagamento devolvido depois do saque) não apaga a conta para sumir com a dívida
      if (avail != null && avail.compareTo(new BigDecimal("-0.01")) < 0) return "A conta tem um débito de R$ " + avail.negate().setScale(2, java.math.RoundingMode.UP).toPlainString().replace('.', ',') + " com o OPUS (pagamento devolvido). Fale com o suporte antes de apagar.";
    } catch (Exception ignored) { }
    Long pendingWd = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_entry_ WHERE professional_id = ? AND kind = 'WITHDRAW' AND status IN ('PENDING','REVIEW')", Long.class, id);
    if (pendingWd != null && pendingWd > 0) return "Há um saque em conferência nesta conta. Espere ele terminar antes de apagar.";
    Long disputes = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE (customer_id = ? OR professional_id = ?) AND dispute_status = 'OPEN'", Long.class, id, id);
    if (disputes != null && disputes > 0) return "Há uma reclamação aberta num pedido desta conta. Espere a equipe OPUS resolver antes de apagar.";
    Long paidHeld = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE professional_id = ? AND COALESCE(pix_paid, FALSE) = TRUE AND COALESCE(pix_released, FALSE) = FALSE AND status NOT IN ('CANCELLED')", Long.class, id);
    if (paidHeld != null && paidHeld > 0) return "Há pagamento guardado no cofre num pedido desta conta. Espere ele ser liberado ou devolvido antes de apagar.";

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
        "DELETE FROM product__professional WHERE professional_id = ?",
        // LGPD: o que ainda ficava para trás depois de apagar (foto do rosto, saúde, corpo, voz, contatos de terceiros, acessos)
        "DELETE FROM user_avatar_ WHERE user_id = ?",
        "DELETE FROM health_profile_ WHERE user_id = ?",
        "DELETE FROM health_sample_ WHERE user_id = ?",
        "DELETE FROM student_measure_ WHERE client_id = ?",
        "DELETE FROM workout_plan_ WHERE client_id = ?",
        "DELETE FROM workout_ WHERE user_id = ?",
        "DELETE FROM emergency_contact_ WHERE user_id = ?",
        "DELETE FROM chat_audio_ WHERE sender_id = ?",
        "DELETE FROM protection_audio_ WHERE session_id IN (SELECT id FROM protection_session_ WHERE user_id = ?)",
        "DELETE FROM protection_consent_ WHERE user_id = ?",
        "DELETE FROM login_history_ WHERE user_id = ?",
        "DELETE FROM bio_token_ WHERE user_id = ?",
        "DELETE FROM pix_key_change_ WHERE user_id = ?",
        "DELETE FROM insurance_interest_ WHERE user_id = ?",
        "DELETE FROM user_notice_ WHERE user_id = ?",
        "DELETE FROM chat_read_ WHERE user_id = ?",
        "DELETE FROM room_member_ WHERE user_id = ?",
        "DELETE FROM post_like_ WHERE user_id = ?",
        "DELETE FROM post_comment_ WHERE user_id = ?",
        "DELETE FROM pro_cover_ WHERE user_id = ?",
        "DELETE FROM pro_reco_ WHERE user_id = ?",
        "DELETE FROM pro_showcase_stat_ WHERE user_id = ?",
        "DELETE FROM pro_certificate_ WHERE professional_id = ?",
        "DELETE FROM pro_course_ WHERE professional_id = ?",
        "DELETE FROM pro_expense_ WHERE professional_id = ?",
        "DELETE FROM pro_blocked_client_ WHERE client_id = ? OR professional_id = ?",
        "DELETE FROM support_message_ WHERE user_id = ?",
        "UPDATE urgent_request_ SET description = 'Pedido apagado', address = NULL, latitude = NULL, longitude = NULL WHERE customer_id = ?",
        "UPDATE message_ SET image_url = NULL WHERE sender_id = ? AND image_url IS NOT NULL"}) {
      try {
        long q = sql.chars().filter(c -> c == '?').count();
        removed += q == 2 ? jdbc.update(sql, id, id) : jdbc.update(sql, id);
      }
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
