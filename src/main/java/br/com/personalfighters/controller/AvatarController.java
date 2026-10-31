package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Foto de perfil guardada no banco do próprio servidor.
 *  PUT    /users/me/avatar  {image: base64 (jpeg/png), contentType?}
 *  DELETE /users/me/avatar
 *  GET    /users/{id}/avatar  (público, devolve a imagem)
 * O campo avatarUrl do usuário guarda o caminho relativo "/users/{id}/avatar?v=...".
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AvatarController {

  private static final int MAX_BYTES = 3 * 1024 * 1024;

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final UserRepository userRepository;

  @PutMapping("/users/me/avatar")
  public ResponseEntity<?> upload(@RequestBody Map<String, String> body) {
    User me = userService.findAuthUser();
    String raw = body.get("image");
    if (raw == null || raw.isBlank()) return bad("Envie uma imagem.");
    String contentType = body.getOrDefault("contentType", "image/jpeg");
    int comma = raw.indexOf(',');
    if (raw.startsWith("data:") && comma > 0) {
      contentType = raw.substring(5, raw.indexOf(';') > 0 ? raw.indexOf(';') : comma);
      raw = raw.substring(comma + 1);
    }
    if (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp")) {
      return bad("Formato de imagem não aceito.");
    }
    byte[] data;
    try {
      data = Base64.getMimeDecoder().decode(raw);
    } catch (IllegalArgumentException e) {
      return bad("Imagem inválida.");
    }
    if (data.length == 0) return bad("Imagem vazia.");
    if (data.length > MAX_BYTES) return bad("A foto é muito grande. Tente outra.");

    Timestamp now = Timestamp.from(Instant.now());
    int updated = jdbc.update("UPDATE user_avatar_ SET content_type = ?, data = ?, updated_at = ? WHERE user_id = ?",
        contentType, data, now, me.getId());
    if (updated == 0) {
      jdbc.update("INSERT INTO user_avatar_ (user_id, content_type, data, updated_at) VALUES (?, ?, ?, ?)",
          me.getId(), contentType, data, now);
    }
    String url = "/users/" + me.getId() + "/avatar?v=" + now.getTime();
    me.setAvatarUrl(url);
    userRepository.save(me);
    log.info("Foto de perfil atualizada para o usuário {} ({} bytes)", me.getId(), data.length);
    return ResponseEntity.ok(Map.of("avatarUrl", url));
  }

  @DeleteMapping("/users/me/avatar")
  public ResponseEntity<?> remove() {
    User me = userService.findAuthUser();
    jdbc.update("DELETE FROM user_avatar_ WHERE user_id = ?", me.getId());
    me.setAvatarUrl(null);
    userRepository.save(me);
    return ResponseEntity.ok(Map.of("avatarUrl", ""));
  }

  @GetMapping("/users/{id}/avatar")
  public ResponseEntity<byte[]> get(@PathVariable Long id) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, data FROM user_avatar_ WHERE user_id = ?", id);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    Map<String, Object> row = rows.get(0);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) row.get("content_type")))
        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
        .body((byte[]) row.get("data"));
  }

  private static ResponseEntity<?> bad(String msg) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }
}
