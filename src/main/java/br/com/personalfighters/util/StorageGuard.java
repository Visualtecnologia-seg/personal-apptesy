package br.com.personalfighters.util;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Protege o banco contra enchimento (vídeos/áudios em massa).
 * - Se o banco passar do limite (DB_MAX_MB, padrão 900 MB), novos arquivos são recusados.
 * - Cotas por pessoa: vídeos por dia e espaço total; áudios por pedido por dia.
 */
public final class StorageGuard {
  private StorageGuard() { }

  private static volatile long checkedAt = 0;
  private static volatile boolean full = false;

  public static boolean dbFull(JdbcTemplate jdbc) {
    long now = System.currentTimeMillis();
    if (now - checkedAt > 60_000) {
      try {
        long max = Long.parseLong(System.getenv().getOrDefault("DB_MAX_MB", "900")) * 1024 * 1024;
        Long size = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        full = size != null && size > max;
      } catch (Exception ignored) { full = false; }
      checkedAt = now;
    }
    return full;
  }

  /** Mensagem de erro, ou null se pode enviar o vídeo. */
  public static String videoQuota(JdbcTemplate jdbc, Object professionalId, long newBytes) {
    if (dbFull(jdbc)) return "O envio de arquivos está pausado no momento. Tente mais tarde.";
    Long today = jdbc.queryForObject("SELECT COUNT(*) FROM post_video_ v JOIN post_ p ON p.id = v.post_id WHERE p.professional_id = ? AND v.created_at > now() - interval '1 day'", Long.class, professionalId);
    if (today != null && today >= 5) return "Limite de 5 vídeos por dia. Tente amanhã.";
    Long total = jdbc.queryForObject("SELECT COALESCE(SUM(v.size_bytes),0) FROM post_video_ v JOIN post_ p ON p.id = v.post_id WHERE p.professional_id = ?", Long.class, professionalId);
    if (total != null && total + newBytes > 400L * 1024 * 1024) return "Seu espaço de vídeos está cheio (400 MB). Apague vídeos antigos para enviar novos.";
    return null;
  }

  public static String audioQuota(JdbcTemplate jdbc, Object orderId, Object senderId) {
    if (dbFull(jdbc)) return "O envio de arquivos está pausado no momento. Tente mais tarde.";
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM chat_audio_ WHERE order_id = ? AND sender_id = ? AND created_at > now() - interval '1 day'", Long.class, orderId, senderId);
    if (n != null && n >= 40) return "Limite de áudios neste pedido por hoje. Escreva uma mensagem.";
    return null;
  }
}
