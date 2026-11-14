package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.util.StorageGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Capa do perfil e indicações de produtos do profissional.
 * - A capa pode ser "só capa" ou "capa com parceria" (com o nome da marca).
 * - Até 3 indicações de produto, cada uma com foto, frase do profissional e link de loja liberada.
 * - Tudo passa pela aprovação do administrador antes de aparecer para os clientes.
 * - Links só de lojas conhecidas (nada de WhatsApp, Instagram, telefone ou site próprio).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ShowcaseController {

  private static final ZoneId Z = ZoneId.of("America/Sao_Paulo");
  private static final int MAX_ITEMS = 3;
  private static final int MAX_BYTES = 3 * 1024 * 1024;

  /** Lojas liberadas: domínio → nome mostrado. Subdomínios também valem (ex.: produto.mercadolivre.com.br). */
  static final Map<String, String> STORES = new LinkedHashMap<>();
  static {
    STORES.put("mercadolivre.com.br", "Mercado Livre");
    STORES.put("mercadolivre.com", "Mercado Livre");
    STORES.put("mercadolibre.com", "Mercado Livre");
    STORES.put("amazon.com.br", "Amazon");
    STORES.put("amzn.to", "Amazon");
    STORES.put("a.co", "Amazon");
    STORES.put("shopee.com.br", "Shopee");
    STORES.put("shp.ee", "Shopee");
    STORES.put("magazineluiza.com.br", "Magalu");
    STORES.put("magalu.com", "Magalu");
    STORES.put("magalu.com.br", "Magalu");
    STORES.put("netshoes.com.br", "Netshoes");
    STORES.put("decathlon.com.br", "Decathlon");
  }
  /** Nada de contato por fora nos textos (telefone, WhatsApp, @perfil, links). */
  private static final Pattern CONTACT = Pattern.compile("(?i)(\\bwhats(app)?\\b|wa\\.me|\\bzap\\b|\\binsta(gram)?\\b|telegram|https?://|www\\.|\\.com\\b|@\\w|(\\d[\\s.-]?){8,})");

  private final JdbcTemplate jdbc;
  private final UserService userService;
  private final NotificationService notificationService;

  // ───────────── Profissional: ver e editar ─────────────

  @GetMapping("/pro/showcase")
  public Map<String, Object> mine() {
    User me = pro();
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> c = jdbc.queryForList("SELECT mode, brand, status, reject_reason AS \"rejectReason\", EXTRACT(EPOCH FROM updated_at)::bigint AS v FROM pro_cover_ WHERE user_id = ?", me.getId());
    out.put("cover", c.isEmpty() ? null : c.get(0));
    out.put("items", jdbc.queryForList("SELECT id, title, note, url, store, kind, featured, status, reject_reason AS \"rejectReason\", clicks, (data IS NOT NULL) AS \"hasImage\", " +
        "EXTRACT(EPOCH FROM updated_at)::bigint AS v FROM pro_reco_ WHERE user_id = ? ORDER BY featured DESC, position, id", me.getId()));
    LocalDate m = LocalDate.now(Z).withDayOfMonth(1);
    List<Map<String, Object>> s = jdbc.queryForList("SELECT views, clicks FROM pro_showcase_stat_ WHERE user_id = ? AND month = ?", me.getId(), java.sql.Date.valueOf(m));
    out.put("stats", s.isEmpty() ? Map.of("views", 0, "clicks", 0) : s.get(0));
    out.put("stores", new LinkedHashSet<>(STORES.values()));
    out.put("max", MAX_ITEMS);
    return out;
  }

  @PutMapping("/pro/showcase/cover")
  public ResponseEntity<?> saveCover(@RequestBody Map<String, Object> b) {
    User me = pro();
    String mode = "PARTNER".equals(b.get("mode")) ? "PARTNER" : "PLAIN";
    String brand = clean(b.get("brand"), 40);
    if ("PARTNER".equals(mode) && (brand == null || brand.length() < 2)) return bad("Escreva o nome da marca parceira.");
    if (brand != null && CONTACT.matcher(brand).find()) return bad("O nome da marca não pode ter link, telefone ou @perfil.");
    if (!"PARTNER".equals(mode)) brand = null;
    Img img = null;
    if (b.get("image") != null) {
      try { img = image(Objects.toString(b.get("image"))); } catch (IllegalArgumentException e) { return bad(e.getMessage()); }
    }
    Timestamp now = ts();
    boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pro_cover_ WHERE user_id = ?)", Boolean.class, me.getId()));
    if (!exists && img == null) return bad("Escolha uma foto para a capa.");
    if (exists) {
      if (img != null) jdbc.update("UPDATE pro_cover_ SET content_type = ?, data = ? WHERE user_id = ?", img.type, img.data, me.getId());
      jdbc.update("UPDATE pro_cover_ SET mode = ?, brand = ?, status = 'PENDING', reject_reason = NULL, updated_at = ?, reviewed_at = NULL WHERE user_id = ?", mode, brand, now, me.getId());
    } else {
      jdbc.update("INSERT INTO pro_cover_ (user_id, content_type, data, mode, brand, status, updated_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?)", me.getId(), img.type, img.data, mode, brand, now);
    }
    return ResponseEntity.ok(Map.of("ok", true, "status", "PENDING"));
  }

  @DeleteMapping("/pro/showcase/cover")
  public Map<String, Object> deleteCover() {
    jdbc.update("DELETE FROM pro_cover_ WHERE user_id = ?", pro().getId());
    return Map.of("ok", true);
  }

  @PostMapping("/pro/showcase/items")
  public ResponseEntity<?> addItem(@RequestBody Map<String, Object> b) {
    User me = pro();
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM pro_reco_ WHERE user_id = ?", Long.class, me.getId());
    if (n != null && n >= MAX_ITEMS) return bad("Você já tem " + MAX_ITEMS + " indicações. Apague uma para colocar outra.");
    return saveItem(me, null, b);
  }

  @PutMapping("/pro/showcase/items/{id}")
  public ResponseEntity<?> editItem(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    User me = pro();
    if (!own(me, id)) return ResponseEntity.notFound().build();
    return saveItem(me, id, b);
  }

  @DeleteMapping("/pro/showcase/items/{id}")
  public Map<String, Object> deleteItem(@PathVariable Long id) {
    jdbc.update("DELETE FROM pro_reco_ WHERE id = ? AND user_id = ?", id, pro().getId());
    return Map.of("ok", true);
  }

  /** Coloca uma indicação em destaque no topo do perfil (só uma por vez). */
  @PostMapping("/pro/showcase/items/{id}/feature")
  public ResponseEntity<?> feature(@PathVariable Long id) {
    User me = pro();
    if (!own(me, id)) return ResponseEntity.notFound().build();
    jdbc.update("UPDATE pro_reco_ SET featured = (id = ?) WHERE user_id = ?", id, me.getId());
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @GetMapping("/pro/showcase/cover-image")
  public ResponseEntity<byte[]> myCover() { return imageOf("SELECT content_type, data FROM pro_cover_ WHERE user_id = ?", pro().getId(), false); }

  @GetMapping("/pro/showcase/items/{id}/image")
  public ResponseEntity<byte[]> myItemImage(@PathVariable Long id) {
    User me = pro();
    if (!own(me, id)) return ResponseEntity.notFound().build();
    return imageOf("SELECT content_type, data FROM pro_reco_ WHERE id = ?", id, false);
  }

  private ResponseEntity<?> saveItem(User me, Long id, Map<String, Object> b) {
    String title = clean(b.get("title"), 80);
    String note = clean(b.get("note"), 160);
    String url = Objects.toString(b.get("url"), "").trim();
    String kind = "PARTNER".equals(b.get("kind")) ? "PARTNER" : "RECO";
    if (title == null || title.length() < 3) return bad("Escreva o nome do produto.");
    if (CONTACT.matcher(title).find() || (note != null && CONTACT.matcher(note).find()))
      return bad("O nome e a frase não podem ter link, telefone, WhatsApp ou @perfil.");
    String store = storeOf(url);
    if (store == null) return bad("Esse link não é aceito. Use um link do Mercado Livre, Amazon, Shopee, Magalu, Netshoes ou Decathlon.");
    Img img = null;
    if (b.get("image") != null) {
      try { img = image(Objects.toString(b.get("image"))); } catch (IllegalArgumentException e) { return bad(e.getMessage()); }
    }
    if (id == null && img == null) return bad("Coloque uma foto do produto.");
    Timestamp now = ts();
    if (id == null) {
      boolean first = Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pro_reco_ WHERE user_id = ?)", Boolean.class, me.getId()));
      Long newId = jdbc.queryForObject("INSERT INTO pro_reco_ (user_id, title, note, url, store, kind, featured, position, content_type, data, status, created_at, updated_at) " +
          "VALUES (?, ?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(position),0)+1 FROM pro_reco_ WHERE user_id = ?), ?, ?, 'PENDING', ?, ?) RETURNING id", Long.class,
          me.getId(), title, note, url, store, kind, first, me.getId(), img.type, img.data, now, now);
      return ResponseEntity.ok(Map.of("ok", true, "id", newId, "status", "PENDING"));
    }
    if (img != null) jdbc.update("UPDATE pro_reco_ SET content_type = ?, data = ? WHERE id = ?", img.type, img.data, id);
    jdbc.update("UPDATE pro_reco_ SET title = ?, note = ?, url = ?, store = ?, kind = ?, status = 'PENDING', reject_reason = NULL, updated_at = ?, reviewed_at = NULL WHERE id = ?",
        title, note, url, store, kind, now, id);
    return ResponseEntity.ok(Map.of("ok", true, "id", id, "status", "PENDING"));
  }

  // ───────────── Público: o que o cliente vê (só o que foi aprovado) ─────────────

  private final Map<String, Long> seen = new ConcurrentHashMap<>();

  @GetMapping("/showcase/{userId}")
  public Map<String, Object> publicShowcase(@PathVariable Long userId, @RequestHeader(value = "X-Forwarded-For", required = false) String ip) {
    Map<String, Object> out = publicData(jdbc, userId);
    // conta visita (no máximo 1 por pessoa/hora)
    String key = userId + "|" + (ip == null ? "" : ip.split(",")[0].trim());
    long nowMs = System.currentTimeMillis();
    Long last = seen.get(key);
    if (last == null || nowMs - last > 3_600_000L) {
      seen.put(key, nowMs);
      if (seen.size() > 50_000) seen.clear();
      bump(userId, "views");
    }
    return out;
  }

  public static Map<String, Object> publicData(JdbcTemplate jdbc, Long userId) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<Map<String, Object>> c = jdbc.queryForList("SELECT mode, brand, EXTRACT(EPOCH FROM updated_at)::bigint AS v FROM pro_cover_ WHERE user_id = ? AND status = 'APPROVED'", userId);
    out.put("cover", c.isEmpty() ? null : c.get(0));
    out.put("items", jdbc.queryForList("SELECT id, title, note, store, kind, featured, EXTRACT(EPOCH FROM updated_at)::bigint AS v FROM pro_reco_ " +
        "WHERE user_id = ? AND status = 'APPROVED' ORDER BY featured DESC, position, id", userId));
    return out;
  }

  @GetMapping("/showcase/{userId}/cover")
  public ResponseEntity<byte[]> publicCover(@PathVariable Long userId) {
    return imageOf("SELECT content_type, data FROM pro_cover_ WHERE user_id = ? AND status = 'APPROVED'", userId, true);
  }

  @GetMapping("/showcase/items/{id}/image")
  public ResponseEntity<byte[]> publicItemImage(@PathVariable Long id) {
    return imageOf("SELECT content_type, data FROM pro_reco_ WHERE id = ? AND status = 'APPROVED'", id, true);
  }

  /** O app chama ao abrir a loja: conta o clique e devolve o link. */
  @PostMapping("/showcase/items/{id}/click")
  public ResponseEntity<?> click(@PathVariable Long id) {
    String url = clickAndUrl(id);
    return url == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("url", url));
  }

  /** Usado na página do link (/p/{id}): conta o clique e manda para a loja. */
  @GetMapping("/showcase/items/{id}/go")
  public ResponseEntity<Void> go(@PathVariable Long id) {
    String url = clickAndUrl(id);
    if (url == null) return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/")).build();
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).header("Referrer-Policy", "no-referrer").build();
  }

  private String clickAndUrl(Long id) {
    List<Map<String, Object>> r = jdbc.queryForList("SELECT user_id, url FROM pro_reco_ WHERE id = ? AND status = 'APPROVED'", id);
    if (r.isEmpty()) return null;
    String url = Objects.toString(r.get(0).get("url"));
    if (storeOf(url) == null) return null; // segurança extra: nunca redireciona para fora das lojas liberadas
    jdbc.update("UPDATE pro_reco_ SET clicks = clicks + 1 WHERE id = ?", id);
    bump(((Number) r.get(0).get("user_id")).longValue(), "clicks");
    return url;
  }

  private void bump(Long userId, String col) {
    try {
      java.sql.Date m = java.sql.Date.valueOf(LocalDate.now(Z).withDayOfMonth(1));
      jdbc.update("INSERT INTO pro_showcase_stat_ (user_id, month, " + col + ") VALUES (?, ?, 1) ON CONFLICT (user_id, month) DO UPDATE SET " + col + " = pro_showcase_stat_." + col + " + 1", userId, m);
    } catch (Exception ignored) { }
  }

  // ───────────── Administrador: aprovar ou recusar ─────────────

  @GetMapping("/admin/showcase/pending")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> pending() {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("covers", jdbc.queryForList("SELECT c.user_id AS \"userId\", c.mode, c.brand, c.updated_at AS \"updatedAt\", u.name AS \"proName\" FROM pro_cover_ c JOIN user_ u ON u.id = c.user_id " +
        "WHERE c.status = 'PENDING' ORDER BY c.updated_at"));
    out.put("items", jdbc.queryForList("SELECT r.id, r.user_id AS \"userId\", r.title, r.note, r.url, r.store, r.kind, r.updated_at AS \"updatedAt\", u.name AS \"proName\" FROM pro_reco_ r JOIN user_ u ON u.id = r.user_id " +
        "WHERE r.status = 'PENDING' ORDER BY r.updated_at"));
    return out;
  }

  @GetMapping("/admin/showcase/cover/{userId}/image")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<byte[]> adminCover(@PathVariable Long userId) { return imageOf("SELECT content_type, data FROM pro_cover_ WHERE user_id = ?", userId, false); }

  @GetMapping("/admin/showcase/items/{id}/image")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public ResponseEntity<byte[]> adminItem(@PathVariable Long id) { return imageOf("SELECT content_type, data FROM pro_reco_ WHERE id = ?", id, false); }

  @PostMapping("/admin/showcase/cover/{userId}/review")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> reviewCover(@PathVariable Long userId, @RequestBody Map<String, Object> b) {
    boolean ok = Boolean.TRUE.equals(b.get("approve"));
    String note = clean(b.get("note"), 200);
    jdbc.update("UPDATE pro_cover_ SET status = ?, reject_reason = ?, reviewed_at = ? WHERE user_id = ?", ok ? "APPROVED" : "REJECTED", ok ? null : note, ts(), userId);
    tell(userId, ok ? "Sua foto de capa foi aprovada e já aparece no seu perfil." : "Sua foto de capa não foi aprovada" + (note != null ? ": " + note : ".") + " Troque em Meu negócio → Capa e indicações.");
    return Map.of("ok", true);
  }

  @PostMapping("/admin/showcase/items/{id}/review")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Map<String, Object> reviewItem(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    boolean ok = Boolean.TRUE.equals(b.get("approve"));
    String note = clean(b.get("note"), 200);
    jdbc.update("UPDATE pro_reco_ SET status = ?, reject_reason = ?, reviewed_at = ? WHERE id = ?", ok ? "APPROVED" : "REJECTED", ok ? null : note, ts(), id);
    try {
      Map<String, Object> r = jdbc.queryForMap("SELECT user_id, title FROM pro_reco_ WHERE id = ?", id);
      tell(((Number) r.get("user_id")).longValue(), ok ? "Sua indicação \"" + r.get("title") + "\" foi aprovada e já aparece no seu perfil."
          : "Sua indicação \"" + r.get("title") + "\" não foi aprovada" + (note != null ? ": " + note : ".") + "");
    } catch (Exception ignored) { }
    return Map.of("ok", true);
  }

  // ───────────── Ajudantes ─────────────

  private void tell(Long userId, String text) {
    try { notificationService.sendToUser(userService.findById(userId), true, text); } catch (Exception ignored) { }
  }

  private User pro() {
    User me = userService.findAuthUser();
    Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id = ? AND role = 'PROFESSIONAL'", Integer.class, me.getId());
    if (ok == null || ok == 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Só para profissionais.");
    return me;
  }

  private boolean own(User me, Long id) {
    return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pro_reco_ WHERE id = ? AND user_id = ?)", Boolean.class, id, me.getId()));
  }

  /** Nome da loja se o link é https de uma loja liberada; senão null. */
  static String storeOf(String url) {
    if (url == null || url.length() > 600 || url.matches(".*\\s.*")) return null;
    try {
      URI u = new URI(url);
      if (!"https".equalsIgnoreCase(u.getScheme()) || u.getRawUserInfo() != null || u.getPort() != -1) return null;
      String host = u.getHost();
      if (host == null) return null;
      host = host.toLowerCase(Locale.ROOT);
      for (Map.Entry<String, String> e : STORES.entrySet())
        if (host.equals(e.getKey()) || host.endsWith("." + e.getKey())) return e.getValue();
    } catch (Exception ignored) { }
    return null;
  }

  private static String clean(Object v, int max) {
    if (v == null) return null;
    String s = v.toString().replaceAll("[\\p{Cntrl}<>]", " ").replaceAll("\\s+", " ").trim();
    if (s.isEmpty()) return null;
    return s.length() > max ? s.substring(0, max) : s;
  }

  private static final class Img { String type; byte[] data; }

  /** Aceita base64 (com ou sem "data:"), só JPEG/PNG/WEBP de verdade (confere os bytes), até 3 MB. */
  private Img image(String raw) {
    if (StorageGuard.dbFull(jdbc)) throw new IllegalArgumentException("O envio de fotos está pausado no momento. Tente mais tarde.");
    int comma = raw.indexOf(',');
    if (raw.startsWith("data:") && comma > 0) raw = raw.substring(comma + 1);
    byte[] d;
    try { d = Base64.getMimeDecoder().decode(raw); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Imagem inválida."); }
    if (d.length < 100) throw new IllegalArgumentException("Imagem inválida.");
    if (d.length > MAX_BYTES) throw new IllegalArgumentException("A foto é muito grande. Tente outra.");
    Img i = new Img();
    i.data = d;
    if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) i.type = "image/jpeg";
    else if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') i.type = "image/png";
    else if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') i.type = "image/webp";
    else throw new IllegalArgumentException("Formato de imagem não aceito. Use JPG ou PNG.");
    return i;
  }

  private ResponseEntity<byte[]> imageOf(String sql, Object id, boolean cache) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
    if (rows.isEmpty() || rows.get(0).get("data") == null) return ResponseEntity.notFound().build();
    ResponseEntity.BodyBuilder r = ResponseEntity.ok().contentType(MediaType.parseMediaType((String) rows.get(0).get("content_type")))
        .header("X-Content-Type-Options", "nosniff");
    r = cache ? r.cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic()) : r.cacheControl(CacheControl.noStore());
    return r.body((byte[]) rows.get(0).get("data"));
  }

  private static Timestamp ts() { return Timestamp.valueOf(LocalDateTime.now(Z)); }
  private static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("message", m)); }
}
