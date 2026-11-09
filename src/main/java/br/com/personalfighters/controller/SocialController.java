package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.service.NotificationService;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Rede social do profissional (L1–L4): posts só com fotos, curtidas e comentários.
 * Só quem já foi atendido pelo profissional comenta; a nota vale uma vez por pedido.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SocialController {

  private static final int MAX_PHOTOS = 6;
  private static final int MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  private final UserService userService;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  // ───────────── Feed ─────────────

  @GetMapping("/professionals/{proId}/posts")
  public List<Map<String, Object>> feed(@PathVariable Long proId, @RequestParam(required = false) String kind) {
    User me = userService.findAuthUser();
    boolean byKind = kind != null && kind.matches("PHOTO|WORK|CLASS");
    List<Map<String, Object>> posts = byKind
        ? jdbc.queryForList("SELECT id, professional_id, caption, created_at, kind, order_id, title, level, modality, hire_button, before_after, views FROM post_ WHERE professional_id = ? AND COALESCE(kind,'PHOTO') = ? ORDER BY created_at DESC LIMIT 60", proId, kind)
        : jdbc.queryForList("SELECT id, professional_id, caption, created_at, kind, order_id, title, level, modality, hire_button, before_after, views FROM post_ WHERE professional_id = ? ORDER BY created_at DESC LIMIT 60", proId);
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> p : posts) out.add(decorate(p, me));
    return out;
  }

  @GetMapping("/professionals/{proId}/social-summary")
  public Map<String, Object> summary(@PathVariable Long proId) {
    User me = userService.findAuthUser();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("posts", count("SELECT COUNT(*) FROM post_ WHERE professional_id = ?", proId));
    out.put("likes", count("SELECT COUNT(*) FROM post_like_ l JOIN post_ p ON p.id = l.post_id WHERE p.professional_id = ?", proId));
    Map<String, Object> r = jdbc.queryForMap(
        "SELECT COUNT(c.rating) AS n, COALESCE(AVG(c.rating), 0) AS avg FROM post_comment_ c JOIN post_ p ON p.id = c.post_id WHERE p.professional_id = ? AND c.rating IS NOT NULL", proId);
    out.put("ratings", r.get("n"));
    out.put("ratingAvg", r.get("avg"));
    out.put("canComment", servedOrder(me.getId(), proId) != null);
    out.put("pendingRatingOrderId", unratedOrder(me.getId(), proId));
    return out;
  }

  @GetMapping("/posts/{postId}")
  public ResponseEntity<?> one(@PathVariable UUID postId) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, professional_id, caption, created_at, kind, order_id, title, level, modality, hire_button, before_after, views FROM post_ WHERE id = ?", postId);
    if (rows.isEmpty()) return bad("Post não encontrado.");
    Map<String, Object> p = decorate(rows.get(0), me);
    p.put("comments", jdbc.queryForList(
        "SELECT id, user_id AS \"userId\", user_name AS \"userName\", text, rating, created_at AS \"createdAt\" FROM post_comment_ WHERE post_id = ? ORDER BY created_at", postId));
    Long proId = ((Number) rows.get(0).get("professional_id")).longValue();
    p.put("canComment", servedOrder(me.getId(), proId) != null);
    p.put("pendingRatingOrderId", unratedOrder(me.getId(), proId));
    return ResponseEntity.ok(p);
  }

  @GetMapping("/posts/photos/{photoId}")
  public ResponseEntity<byte[]> photo(@PathVariable UUID photoId) {
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT content_type, data FROM post_photo_ WHERE id = ?", photoId);
    if (rows.isEmpty()) return ResponseEntity.notFound().build();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType((String) rows.get(0).get("content_type")))
        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
        .body((byte[]) rows.get(0).get("data"));
  }

  // ───────────── Profissional publica ─────────────

  @PostMapping("/posts")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    if (!hasRole(me, Role.PROFESSIONAL)) return bad("Só profissionais publicam.");
    String kind = Objects.toString(body.get("kind"), "PHOTO").toUpperCase();
    if (!kind.matches("PHOTO|WORK|CLASS")) kind = "PHOTO";
    Object photos = body.get("photos");
    boolean hasPhotos = photos instanceof List && !((List<?>) photos).isEmpty();
    if (!"CLASS".equals(kind) && !hasPhotos) return bad("Escolha pelo menos uma foto.");
    String title = Objects.toString(body.get("title"), "").trim();
    if ("CLASS".equals(kind) && title.length() < 3) return bad("Dê um título para a aula.");
    UUID orderId = null;
    if ("WORK".equals(kind) && body.get("orderId") != null) {
      try {
        UUID oid = UUID.fromString(body.get("orderId").toString());
        Long ok = jdbc.queryForObject("SELECT COUNT(*) FROM order_ WHERE id = ? AND professional_id = ? AND status IN ('DONE','REVIEWED','PENDING_CLIENT_CONFIRMATION')", Long.class, oid, me.getId());
        if (ok != null && ok > 0) orderId = oid;
      } catch (Exception ignored) { }
    }
    UUID id = UUID.randomUUID();
    String caption = Objects.toString(body.get("caption"), "").trim();
    if (caption.length() > 1000) caption = caption.substring(0, 1000);
    jdbc.update("INSERT INTO post_ (id, professional_id, caption, created_at, kind, order_id, title, level, modality, hire_button, before_after) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        id, me.getId(), caption, Timestamp.valueOf(LocalDateTime.now()), kind, orderId, title.isEmpty() ? null : title.substring(0, Math.min(140, title.length())),
        trim(body.get("level"), 20), trim(body.get("modality"), 60), !Boolean.FALSE.equals(body.get("hireButton")), Boolean.TRUE.equals(body.get("beforeAfter")));
    int pos = 0;
    if (hasPhotos) for (Object item : (List<?>) photos) {
      if (pos >= MAX_PHOTOS) break;
      if (savePhoto(id, pos, item)) pos++;
    }
    if (pos == 0 && !"CLASS".equals(kind)) {
      jdbc.update("DELETE FROM post_ WHERE id = ?", id);
      return bad("Não foi possível ler as fotos. Tente fotos menores.");
    }
    if (orderId != null) {
      try {
        Long cid = jdbc.queryForObject("SELECT customer_id FROM order_ WHERE id = ?", Long.class, orderId);
        notificationService.sendToUser(userService.findById(cid), false, firstName(me) + " publicou o trabalho que fez para você. Toque para ver e deixar sua avaliação!");
      } catch (Exception ignored) { }
    }
    try {
      for (Long f : jdbc.queryForList("SELECT user_id FROM follow_ WHERE professional_id = ? LIMIT 300", Long.class, me.getId()))
        notificationService.sendToUser(userService.findById(f), false, firstName(me) + ("CLASS".equals(kind) ? " publicou uma aula nova: " + title : " fez uma publicação nova."));
    } catch (Exception ignored) { }
    return ResponseEntity.ok(Map.of("id", id, "photos", pos));
  }

  private static String trim(Object o, int max) { String s = Objects.toString(o, "").trim(); return s.isEmpty() ? null : s.substring(0, Math.min(max, s.length())); }

  private static final long MAX_VIDEO_BYTES = 60L * 1024 * 1024;

  /** Vídeo da aula (até 5 minutos). */
  @PostMapping(value = "/posts/{postId}/video", consumes = "multipart/form-data")
  public ResponseEntity<?> video(@PathVariable UUID postId, @RequestParam("file") org.springframework.web.multipart.MultipartFile file, @RequestParam(value = "seconds", required = false) Integer seconds) {
    User me = userService.findAuthUser();
    Long mine = jdbc.queryForObject("SELECT COUNT(*) FROM post_ WHERE id = ? AND professional_id = ?", Long.class, postId, me.getId());
    if (mine == null || mine == 0) return bad("Publicação não encontrada.");
    if (seconds != null && seconds > 305) return bad("O vídeo pode ter no máximo 5 minutos.");
    if (file == null || file.isEmpty()) return bad("Vídeo vazio.");
    if (file.getSize() > MAX_VIDEO_BYTES) return bad("O vídeo ficou grande demais (máximo 60 MB). Grave em qualidade menor ou mais curto.");
    String quota = br.com.personalfighters.util.StorageGuard.videoQuota(jdbc, me.getId(), file.getSize());
    if (quota != null) return bad(quota);
    String type = br.com.personalfighters.util.MediaTypes.videoOr(file.getContentType(), "video/mp4");
    try {
      jdbc.update("DELETE FROM post_video_ WHERE post_id = ?", postId);
      UUID vid = UUID.randomUUID();
      jdbc.update("INSERT INTO post_video_ (id, post_id, content_type, seconds, size_bytes, data, created_at) VALUES (?,?,?,?,?,?,?)",
          vid, postId, type, seconds, file.getSize(), file.getBytes(), Timestamp.valueOf(LocalDateTime.now()));
      return ResponseEntity.ok(Map.of("id", vid));
    } catch (Exception e) {
      log.warn("Vídeo do post {} falhou: {}", postId, e.getMessage());
      return bad("Não foi possível salvar o vídeo. Tente de novo.");
    }
  }

  /** Toca o vídeo aos pedaços (o celular pede partes com Range). */
  @GetMapping("/posts/videos/{videoId}")
  public ResponseEntity<byte[]> playVideo(@PathVariable UUID videoId, @RequestHeader(value = "Range", required = false) String range) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT post_id, content_type, size_bytes FROM post_video_ WHERE id = ?", videoId);
    if (r.isEmpty()) return ResponseEntity.notFound().build();
    long size = ((Number) r.get(0).get("size_bytes")).longValue();
    String type = Objects.toString(r.get(0).get("content_type"), "video/mp4");
    long start = 0, end = size - 1;
    boolean partial = false;
    if (range != null && range.startsWith("bytes=")) {
      String[] p = range.substring(6).split("-", 2);
      try { if (!p[0].isEmpty()) start = Long.parseLong(p[0]); if (p.length > 1 && !p[1].isEmpty()) end = Long.parseLong(p[1]); partial = true; } catch (Exception ignored) { }
    }
    if (partial && end - start > 2L * 1024 * 1024) end = start + 2L * 1024 * 1024 - 1;
    if (end >= size) end = size - 1;
    if (start > end) return ResponseEntity.status(416).build();
    int len = (int) (end - start + 1);
    byte[] chunk = jdbc.queryForObject("SELECT SUBSTRING(data FROM ? FOR ?) FROM post_video_ WHERE id = ?", byte[].class, (int) start + 1, len, videoId);
    if (start == 0) try { jdbc.update("UPDATE post_ SET views = COALESCE(views,0) + 1 WHERE id = ?", r.get(0).get("post_id")); } catch (Exception ignored) { }
    return ResponseEntity.status(partial ? 206 : 200)
        .header("Accept-Ranges", "bytes")
        .header("Content-Range", "bytes " + start + "-" + end + "/" + size)
        .contentType(MediaType.parseMediaType(type))
        .contentLength(len)
        .body(chunk);
  }

  @DeleteMapping("/posts/{postId}")
  public ResponseEntity<?> delete(@PathVariable UUID postId) {
    User me = userService.findAuthUser();
    int n = jdbc.update("DELETE FROM post_ WHERE id = ? AND professional_id = ?", postId, me.getId());
    if (n == 0 && !hasRole(me, Role.ADMIN)) return bad("Post não encontrado.");
    if (n == 0) jdbc.update("DELETE FROM post_ WHERE id = ?", postId);
    jdbc.update("DELETE FROM post_photo_ WHERE post_id = ?", postId);
    jdbc.update("DELETE FROM post_like_ WHERE post_id = ?", postId);
    jdbc.update("DELETE FROM post_comment_ WHERE post_id = ?", postId);
    jdbc.update("DELETE FROM post_video_ WHERE post_id = ?", postId);
    return ResponseEntity.ok(Map.of("deleted", true));
  }

  // ───────────── Curtir e comentar ─────────────

  @PostMapping("/posts/{postId}/like")
  public Map<String, Object> like(@PathVariable UUID postId) {
    User me = userService.findAuthUser();
    int removed = jdbc.update("DELETE FROM post_like_ WHERE post_id = ? AND user_id = ?", postId, me.getId());
    if (removed == 0) {
      jdbc.update("INSERT INTO post_like_ (post_id, user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
          postId, me.getId(), Timestamp.valueOf(LocalDateTime.now()));
    }
    return Map.of("liked", removed == 0,
        "likes", count("SELECT COUNT(*) FROM post_like_ WHERE post_id = ?", postId));
  }

  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<?> comment(@PathVariable UUID postId, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    List<Map<String, Object>> rows = jdbc.queryForList("SELECT professional_id FROM post_ WHERE id = ?", postId);
    if (rows.isEmpty()) return bad("Post não encontrado.");
    Long proId = ((Number) rows.get(0).get("professional_id")).longValue();
    boolean isOwner = proId.equals(me.getId());
    String text = Objects.toString(body.get("text"), "").trim();
    if (text.isEmpty()) return bad("Escreva o seu comentário.");
    if (text.length() > 600) text = text.substring(0, 600);

    UUID orderId = null;
    Integer rating = null;
    if (!isOwner) {
      orderId = servedOrder(me.getId(), proId);
      if (orderId == null) return bad("Só quem já foi atendido por este profissional pode comentar.");
      Object r = body.get("rating");
      if (r != null) {
        int val = (int) Math.round(Double.parseDouble(r.toString()));
        if (val < 1 || val > 5) return bad("A nota vai de 1 a 5.");
        UUID unrated = unratedOrder(me.getId(), proId);
        if (unrated == null) return bad("Você já avaliou todos os seus atendimentos com este profissional.");
        orderId = unrated;
        rating = val;
      }
    }
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO post_comment_ (id, post_id, user_id, user_name, order_id, text, rating, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id, postId, me.getId(), firstName(me), orderId, text, rating, Timestamp.valueOf(LocalDateTime.now()));
    if (!isOwner) {
      try {
        User pro = userService.findById(proId);
        notificationService.sendToUser(pro, true, firstName(me) + (rating != null ? " avaliou o seu trabalho com " + rating + " estrelas." : " comentou no seu post."));
      } catch (Exception e) {
        log.warn("Aviso de comentário não enviado: {}", e.getMessage());
      }
    }
    return ResponseEntity.ok(Map.of("id", id));
  }

  @DeleteMapping("/posts/comments/{commentId}")
  public ResponseEntity<?> deleteComment(@PathVariable UUID commentId) {
    User me = userService.findAuthUser();
    int n = jdbc.update("DELETE FROM post_comment_ WHERE id = ? AND (user_id = ? OR post_id IN (SELECT id FROM post_ WHERE professional_id = ?)) AND rating IS NULL",
        commentId, me.getId(), me.getId());
    return n > 0 ? ResponseEntity.ok(Map.of("deleted", true)) : bad("Comentário não encontrado (avaliações não podem ser apagadas).");
  }

  // ───────────── utilitários ─────────────

  private Map<String, Object> decorate(Map<String, Object> p, User me) {
    UUID id = (UUID) p.get("id");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("professionalId", p.get("professional_id"));
    out.put("caption", p.get("caption"));
    out.put("createdAt", p.get("created_at"));
    out.put("photos", jdbc.queryForList("SELECT id FROM post_photo_ WHERE post_id = ? ORDER BY position", UUID.class, id));
    out.put("likes", count("SELECT COUNT(*) FROM post_like_ WHERE post_id = ?", id));
    out.put("liked", count("SELECT COUNT(*) FROM post_like_ WHERE post_id = ? AND user_id = ?", id, me.getId()) > 0);
    out.put("comments", count("SELECT COUNT(*) FROM post_comment_ WHERE post_id = ?", id));
    List<Map<String, Object>> top = jdbc.queryForList(
        "SELECT user_name AS \"userName\", text, rating FROM post_comment_ WHERE post_id = ? ORDER BY (rating IS NULL), created_at DESC LIMIT 1", id);
    out.put("topComment", top.isEmpty() ? null : top.get(0));
    out.put("kind", Objects.toString(p.get("kind"), "PHOTO"));
    out.put("title", p.get("title"));
    out.put("level", p.get("level"));
    out.put("modality", p.get("modality"));
    out.put("hireButton", !Boolean.FALSE.equals(p.get("hire_button")));
    out.put("beforeAfter", Boolean.TRUE.equals(p.get("before_after")));
    out.put("views", p.get("views") == null ? 0 : p.get("views"));
    List<Map<String, Object>> v = jdbc.queryForList("SELECT id, seconds FROM post_video_ WHERE post_id = ? ORDER BY created_at DESC LIMIT 1", id);
    out.put("video", v.isEmpty() ? null : v.get(0));
    Object oid = p.get("order_id");
    if (oid != null) {
      List<Map<String, Object>> o = jdbc.queryForList("SELECT o.date, c.name FROM order_ o LEFT JOIN user_ c ON c.id = o.customer_id WHERE o.id = ?", oid);
      if (!o.isEmpty()) {
        String nm = Objects.toString(o.get(0).get("name"), "Cliente").trim();
        String[] parts = nm.split(" ");
        out.put("client", parts[0] + (parts.length > 1 ? " " + parts[parts.length - 1].charAt(0) + "." : ""));
        out.put("date", o.get(0).get("date"));
      }
      List<Map<String, Object>> rv = jdbc.queryForList("SELECT rating, comment FROM review_ WHERE order_id = ? AND rating IS NOT NULL AND COALESCE(type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO' ORDER BY id DESC LIMIT 1", oid);
      if (rv.isEmpty()) rv = jdbc.queryForList("SELECT rating, text AS comment FROM post_comment_ WHERE order_id = ? AND rating IS NOT NULL ORDER BY created_at DESC LIMIT 1", oid);
      out.put("review", rv.isEmpty() ? null : rv.get(0));
    }
    return out;
  }

  /** Pedido concluído entre o cliente e o profissional (habilita comentar). */
  private UUID servedOrder(Long customerId, Long proId) {
    List<UUID> ids = jdbc.queryForList(
        "SELECT id FROM order_ WHERE customer_id = ? AND professional_id = ? AND status IN ('DONE', 'REVIEWED') ORDER BY date DESC",
        UUID.class, customerId, proId);
    return ids.isEmpty() ? null : ids.get(0);
  }

  /** Pedido concluído que ainda não recebeu nota num post. */
  private UUID unratedOrder(Long customerId, Long proId) {
    List<UUID> ids = jdbc.queryForList(
        "SELECT o.id FROM order_ o WHERE o.customer_id = ? AND o.professional_id = ? AND o.status IN ('DONE', 'REVIEWED') " +
            "AND NOT EXISTS (SELECT 1 FROM post_comment_ c WHERE c.order_id = o.id AND c.rating IS NOT NULL) ORDER BY o.date DESC",
        UUID.class, customerId, proId);
    return ids.isEmpty() ? null : ids.get(0);
  }

  private boolean savePhoto(UUID postId, int position, Object item) {
    try {
      String raw = Objects.toString(item, "");
      String type = "image/jpeg";
      int comma = raw.indexOf(',');
      if (raw.startsWith("data:") && comma > 0) {
        int semi = raw.indexOf(';');
        type = raw.substring(5, semi > 0 ? semi : comma);
        raw = raw.substring(comma + 1);
      }
      if (!type.startsWith("image/")) return false;
      byte[] data = Base64.getMimeDecoder().decode(raw);
      if (data.length == 0 || data.length > MAX_PHOTO_BYTES) return false;
      jdbc.update("INSERT INTO post_photo_ (id, post_id, position, content_type, data) VALUES (?, ?, ?, ?, ?)",
          UUID.randomUUID(), postId, position, type, data);
      return true;
    } catch (Exception e) {
      log.warn("Foto do post {} não salva: {}", postId, e.getMessage());
      return false;
    }
  }

  private long count(String sql, Object... args) {
    Long n = jdbc.queryForObject(sql, Long.class, args);
    return n == null ? 0 : n;
  }

  static boolean hasRole(User u, Role r) {
    return u != null && u.getRole() != null && u.getRole().contains(r);
  }

  static String firstName(String name) {
    if (name == null || name.isBlank()) return "Alguém";
    return name.trim().split("\\s+")[0];
  }

  static String firstName(User u) {
    if (u == null || u.getName() == null || u.getName().isBlank()) return "Alguém";
    return u.getName().trim().split("\\s+")[0];
  }

  static ResponseEntity<?> bad(String msg) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }
}
