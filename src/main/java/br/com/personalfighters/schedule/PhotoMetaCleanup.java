package br.com.personalfighters.schedule;

import br.com.personalfighters.util.ImageMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Uma vez só: tira GPS/modelo do celular escondidos das fotos que já estavam guardadas
 * (perfil, publicações, provas do serviço, recibos, certificados, capas, indicações e chat).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoMetaCleanup {

  private final JdbcTemplate jdbc;

  private static final String[][] BLOBS = {
      {"user_avatar_", "user_id", "data"}, {"post_photo_", "id", "data"}, {"order_photo_", "id", "data"},
      {"order_material_", "id", "receipt"}, {"pro_certificate_", "id", "photo"}, {"pro_cover_", "user_id", "data"}, {"pro_reco_", "id", "data"}};

  @EventListener(ApplicationReadyEvent.class)
  public void run() {
    new Thread(this::cleanOnce, "limpa-dados-das-fotos").start();
  }

  void cleanOnce() {
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS app_secret_ (name VARCHAR(40) PRIMARY KEY, value TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW())");
      Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM app_secret_ WHERE name = 'photos_meta_clean'", Integer.class);
      if (done != null && done > 0) return;
      int changed = 0;
      for (String[] t : BLOBS) {
        List<Object> ids = jdbc.queryForList("SELECT " + t[1] + " FROM " + t[0] + " WHERE " + t[2] + " IS NOT NULL", Object.class);
        for (Object id : ids) {
          try {
            byte[] d = jdbc.queryForObject("SELECT " + t[2] + " FROM " + t[0] + " WHERE " + t[1] + " = ?", byte[].class, id);
            byte[] c = ImageMeta.strip(d);
            if (c != d) { jdbc.update("UPDATE " + t[0] + " SET " + t[2] + " = ? WHERE " + t[1] + " = ?", c, id); changed++; }
          } catch (Exception e) { log.debug("foto {} {}: {}", t[0], id, e.getMessage()); }
        }
      }
      List<Object> ids = jdbc.queryForList("SELECT id FROM message_ WHERE image_url LIKE 'data:image/%'", Object.class);
      for (Object id : ids) {
        try {
          String img = jdbc.queryForObject("SELECT image_url FROM message_ WHERE id = ?", String.class, id);
          int comma = img.indexOf(',');
          byte[] d = Base64.getMimeDecoder().decode(img.substring(comma + 1));
          byte[] c = ImageMeta.strip(d);
          if (c != d) { jdbc.update("UPDATE message_ SET image_url = ? WHERE id = ?", img.substring(0, comma + 1) + Base64.getEncoder().encodeToString(c), id); changed++; }
        } catch (Exception e) { log.debug("foto do chat {}: {}", id, e.getMessage()); }
      }
      jdbc.update("INSERT INTO app_secret_ (name, value) VALUES ('photos_meta_clean', ?) ON CONFLICT (name) DO NOTHING", String.valueOf(changed));
      log.info("Dados escondidos (GPS etc.) removidos de {} fotos já guardadas", changed);
    } catch (Exception e) {
      log.warn("Limpeza dos dados das fotos não terminou: {}", e.getMessage());
    }
  }
}
