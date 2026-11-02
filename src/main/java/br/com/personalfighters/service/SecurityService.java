package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Proteções contra acesso indevido: aparelho novo, "não fui eu", espera de 24 h no saque
 * depois de trocar a chave PIX, documentos só com permissão (e registro de quem abriu).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

  public static final int PIX_HOLD_HOURS = 24;
  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final NotificationService notificationService;
  private final UserRepository userRepository;

  static Timestamp now() { return Timestamp.valueOf(LocalDateTime.now(Z)); }

  public void event(Long userId, String kind, String detail) {
    jdbc.update("INSERT INTO security_event_ (user_id, kind, detail, created_at) VALUES (?, ?, ?, ?)", userId, kind,
        detail == null ? null : detail.length() > 400 ? detail.substring(0, 400) : detail, now());
  }

  /** Guarda o aparelho do login. Devolve true quando é um celular novo de quem já tinha outro. */
  public boolean registerDevice(User u, String deviceId, String deviceName, String ip) {
    if (u == null || deviceId == null || deviceId.isBlank()) return false;
    String dev = deviceId.trim().length() > 80 ? deviceId.trim().substring(0, 80) : deviceId.trim();
    String name = deviceName == null ? null : deviceName.length() > 120 ? deviceName.substring(0, 120) : deviceName;
    int known = jdbc.update("UPDATE user_device_ SET last_seen = ?, ip = ?, device_name = COALESCE(?, device_name) WHERE user_id = ? AND device_id = ?", now(), ip, name, u.getId(), dev);
    if (known > 0) return false;
    Long others = jdbc.queryForObject("SELECT COUNT(*) FROM user_device_ WHERE user_id = ?", Long.class, u.getId());
    jdbc.update("INSERT INTO user_device_ (user_id, device_id, device_name, first_seen, last_seen, ip) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, device_id) DO NOTHING",
        u.getId(), dev, name, now(), now(), ip);
    if (others == null || others == 0) return false;
    event(u.getId(), "NEW_DEVICE", Objects.toString(name, "Celular novo"));
    String when = LocalDateTime.now(Z).format(DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm"));
    String text = "Novo acesso à sua conta OPUS em " + Objects.toString(name, "outro celular") + " (" + when + "). Foi você? Se não foi, abra o app → Perfil → Segurança → Não fui eu.";
    try { notificationService.sendToUser(u, false, text); } catch (Exception ignored) { }
    try { notificationService.sendToUser(u, true, text); } catch (Exception ignored) { }
    return true;
  }

  /** Até quando o saque fica segurado (troca de chave PIX ou "não fui eu" nas últimas 24 h). */
  public LocalDateTime withdrawHoldUntil(Long userId) {
    Timestamp t = jdbc.query("SELECT GREATEST(COALESCE(u.pix_key_changed_at, 'epoch'::timestamp), " +
            "COALESCE((SELECT MAX(created_at) FROM security_event_ e WHERE e.user_id = u.id AND e.kind = 'NOT_ME'), 'epoch'::timestamp)) FROM user_ u WHERE u.id = ?",
        rs -> rs.next() ? rs.getTimestamp(1) : null, userId);
    if (t == null) return null;
    LocalDateTime until = t.toLocalDateTime().plusHours(PIX_HOLD_HOURS);
    return until.isAfter(LocalDateTime.now(Z)) ? until : null;
  }

  // ───────────── Documentos: só quem tem permissão, e fica registrado ─────────────

  public boolean canViewDocs(Long adminId) {
    Boolean ok = jdbc.query("SELECT can_view_docs FROM panel_permission_ WHERE user_id = ?", rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, adminId);
    return Boolean.TRUE.equals(ok);
  }

  public void logDocAccess(User admin, Long subjectUserId, String what) {
    jdbc.update("INSERT INTO doc_access_log_ (admin_id, subject_user_id, what, created_at) VALUES (?, ?, ?, ?)", admin.getId(), subjectUserId, what, now());
    String subject = subjectUserId == null ? "" : Objects.toString(jdbc.query("SELECT name FROM user_ WHERE id = ?", rs -> rs.next() ? rs.getString(1) : "", subjectUserId), "");
    String login = admin.getUsername() != null && admin.getUsername().startsWith("painel:") ? admin.getUsername().substring(7) : Objects.toString(admin.getUsername(), "");
    jdbc.update("INSERT INTO admin_action_ (admin_id, admin_name, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        admin.getId(), "Administrador (" + login + ")", "Abriu documento", subject, what, now());
  }

  /** Avisa o profissional quando a chave PIX dele é trocada (a troca é registrada pelo banco de dados). */
  @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
  public void notifyPixChanges() {
    try {
      for (Map<String, Object> r : jdbc.queryForList("SELECT c.id, c.user_id, c.new_key_end, c.old_key_end FROM pix_key_change_ c WHERE COALESCE(c.notified, FALSE) = FALSE ORDER BY c.id LIMIT 50")) {
        jdbc.update("UPDATE pix_key_change_ SET notified = TRUE WHERE id = ?", r.get("id"));
        if (Objects.toString(r.get("old_key_end"), "").isEmpty()) continue; // primeiro cadastro
        Long uid = ((Number) r.get("user_id")).longValue();
        event(uid, "PIX_CHANGED", "Chave terminada em " + r.get("old_key_end") + " → " + r.get("new_key_end"));
        try {
          userRepository.findById(uid).ifPresent(u -> notificationService.sendToUser(u, true, "A sua chave PIX da OPUS foi trocada. Por segurança, saques ficam liberados em 24 horas. Não foi você? Abra o app → Meu negócio → Segurança → Não fui eu."));
        } catch (Exception ignored) { }
      }
    } catch (Exception e) { log.debug("Aviso de troca de PIX: {}", e.getMessage()); }
  }

}
