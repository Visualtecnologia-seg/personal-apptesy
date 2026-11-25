package br.com.personalfighters.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 1) Limite de pedidos por aparelho/rede (IP): segura robôs no cadastro, "esqueci a senha",
 *    login, busca, mensagens e envio de arquivos.
 * 2) Cabeçalhos de proteção: arquivos enviados pelos usuários nunca rodam código no navegador,
 *    e o painel só carrega scripts do próprio servidor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProtectionFilter extends OncePerRequestFilter {

  /** Banco (para saber se está cheio). Opcional: sem ele, só a cota por pessoa vale. */
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  /** Cota diária de envios grandes (fotos, vídeos, áudios) por pessoa ou rede. */
  static final long DAILY_UPLOAD_BYTES = 350L * 1024 * 1024;
  private final Map<String, long[]> uploads = new ConcurrentHashMap<>(); // {início do dia, bytes}

  private static final org.springframework.web.util.UrlPathHelper PATHS = new org.springframework.web.util.UrlPathHelper();

  private static final class Window { long start; int count; }
  private final Map<String, Window> hits = new ConcurrentHashMap<>();

  private static final long MIN = 60_000L, HOUR = 3_600_000L;
  private static final long MB = 1024L * 1024L;

  // Envios grandes ao mesmo tempo derrubavam o servidor (memória de 512 MB): no máximo 2 médios e 1 muito grande por vez
  private final Semaphore bigUploads = new Semaphore(2);
  private final Semaphore hugeUploads = new Semaphore(1);

  /** Tamanho máximo do envio por endereço. */
  static long maxBody(String m, String path) {
    if (!"POST".equals(m) && !"PUT".equals(m) && !"PATCH".equals(m)) return 64 * 1024;
    if (path.matches("/posts/[^/]+/video")) return 62 * MB;                            // vídeo (60 MB)
    if (path.equals("/posts") || path.matches("/orders/[^/]+/(finish|problem)")) return 26 * MB; // até 6 fotos
    if (path.matches("/chat/[^/]+/audio") || path.matches("/sos/[^/]+/audio")) return 6 * MB;
    return 10 * MB;                                                                   // uma foto e o resto
  }

  private static final java.util.Set<String> ALLOWED_METHODS = java.util.Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String raw = req.getRequestURI();
    // Caminho do jeito que o servidor entende (decodificado, sem ";..."): "/%61uth" e "/auth;x" contam como "/auth".
    // Antes as travas olhavam o endereço cru e o disfarce furava todos os limites de tentativas.
    String path = PATHS.getPathWithinApplication(req);
    String m = req.getMethod();
    if (raw.indexOf(';') >= 0 || raw.matches(".*%(2[eE]|25|00).*")) { res.sendError(400); return; }
    // Só os métodos que o app usa (TRACE/TRACK/PROPFIND e outros estranhos param aqui)
    if (!ALLOWED_METHODS.contains(m)) { res.setHeader("Allow", "GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD"); res.sendError(405); return; }
    // Ordenação de listas: só nomes simples de campo. Nomes gigantes ou estranhos enchiam a memória / estouravam a pilha
    // (falhas conhecidas da biblioteca de paginação, CVE-2026-41711 e CVE-2026-41716)
    String[] sorts = req.getParameterValues("sort");
    if (sorts != null) {
      if (sorts.length > 3) { res.sendError(400); return; }
      for (String srt : sorts) if (srt == null || !srt.matches("[A-Za-z][A-Za-z0-9_]{0,39}(,(?i:asc|desc))?")) { res.sendError(400); return; }
    }
    // Cabeçalhos de proteção escritos aqui também, sempre (falha conhecida em que a biblioteca de segurança deixava de escrevê-los, CVE-2026-22732)
    res.setHeader("X-Content-Type-Options", "nosniff");
    if (res.getHeader("X-Frame-Options") == null) res.setHeader("X-Frame-Options", "SAMEORIGIN");
    res.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    if (res.getHeader("Referrer-Policy") == null) res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    String ip = clientIp(req);

    // Endereço da API com barra no fim ("/x/") ou caracteres de desvio: recusa (evita furar travas por variação do caminho)
    if (path.length() > 1 && path.endsWith("/") && !(path.startsWith("/painel/") || path.startsWith("/app/") || path.startsWith("/static/"))
        || raw.contains("//") || raw.contains("\\") || raw.contains("%2f") || raw.contains("%2F") || raw.contains("%5c") || raw.contains("%5C") || path.contains("\\")) {
      res.sendError(404);
      return;
    }

    if (!allow(ip, "all", 600, MIN)
        || ("POST".equals(m) && path.equals("/users") && !allow(ip, "signup", 5, HOUR))
        || ("POST".equals(m) && path.equals("/auth/forgot-password") && !allow(ip, "forgot", 5, HOUR))
        || ("POST".equals(m) && path.equals("/auth/reset-password") && !allow(ip, "reset", 15, HOUR))
        || ("POST".equals(m) && path.equals("/auth/phone/send") && !allow(ip, "sms", 8, HOUR))
        || ("POST".equals(m) && (path.equals("/auth/phone/verify") || path.equals("/auth/phone/login")) && !allow(ip, "smsv", 30, HOUR))
        || ("POST".equals(m) && path.equals("/auth/email/send") && !allow(ip, "mail", 8, HOUR))
        || ("POST".equals(m) && path.equals("/auth/email/verify") && !allow(ip, "mailv", 30, HOUR))
        || ("POST".equals(m) && (path.equals("/auth/google") || path.equals("/auth/apple") || path.equals("/auth/bio/login")) && !allow(ip, "google", 30, HOUR))
        || ("POST".equals(m) && (path.equals("/auth") || path.equals("/panel-auth/login") || path.equals("/panel-auth/2fa")) && !allow(ip, "login", 30, 10 * MIN))
        || (path.startsWith("/search") && !allow(ip, "search", 120, MIN))
        || ("POST".equals(m) && path.equals("/messages") && !allow(ip, "msg", 60, MIN))
        || ("POST".equals(m) && path.equals("/partners/lead") && !allow(ip, "lead", 5, HOUR))
        || ("POST".equals(m) && path.startsWith("/excluir-conta") && !allow(ip, "erase", 5, HOUR))
        || ("POST".equals(m) && path.matches("/orders/[^/]+/coupon") && !allow(ip, "coupon", 20, HOUR))          // ninguém fica chutando cupons
        || ("POST".equals(m) && path.equals("/verifications/check-image") && !allow(ip, "imgcheck", 30, 10 * MIN)) // análise de foto pesa no servidor
        || ("POST".equals(m) && path.startsWith("/showcase/") && !allow(ip, "showclick", 30, MIN))
        || (("PUT".equals(m) || "POST".equals(m)) && path.startsWith("/pro/showcase") && !allow(ip, "showedit", 30, 10 * MIN))
        || ("POST".equals(m) && isUpload(req) && !allow(ip, "upload", 40, 10 * MIN))) {
      res.setStatus(429);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"message\":\"Muitas tentativas seguidas. Espere um pouco e tente de novo.\"}");
      return;
    }

    // Tamanho do envio: recusa antes de ler (inclusive envio "picotado" sem tamanho declarado)
    long limit = maxBody(m, path);
    long declared = req.getContentLengthLong();
    if (declared > limit) { tooBig(res); return; }
    // Envios grandes: banco quase cheio → pausa; e cada pessoa tem uma cota por dia
    // (antes uma conta enchia o banco de 1 GB em menos de 1 minuto mandando fotos no chat e o app inteiro parava)
    long cost = declared >= 0 ? declared : (("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)) ? Math.min(limit, 6 * MB) : 0);
    // Todo envio conta na cota (não só os grandes): textos longos repetidos também enchiam o banco
    if (cost > 0 && !path.startsWith("/panel-auth") && !path.startsWith("/admin") && !path.startsWith("/auth")) {
      if (cost > 16 * 1024 && jdbc != null && br.com.personalfighters.util.StorageGuard.dbFull(jdbc)) {
        res.setStatus(507);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"O envio de fotos e arquivos está pausado no momento. Tente mais tarde.\"}");
        return;
      }
      if (!spendUpload(uploadKey(req, ip), cost)) {
        res.setStatus(429);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"Você atingiu o limite de envios de fotos e arquivos por hoje. Tente amanhã.\"}");
        return;
      }
    }
    HttpServletRequest body = declared < 0 && !"GET".equals(m) && !"HEAD".equals(m) ? new LimitedRequest(req, limit) : req;
    Semaphore gate = declared > 20 * MB || declared < 0 && limit > 20 * MB ? hugeUploads : declared > 4 * MB ? bigUploads : null;
    if (gate != null) {
      boolean got;
      try { got = gate.tryAcquire(25, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); got = false; }
      if (!got) {
        res.setStatus(503);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"message\":\"Muitos envios ao mesmo tempo. Tente de novo em instantes.\"}");
        return;
      }
    }
    try {
      headers(path, res);
      chain.doFilter(body, res);
    } finally {
      if (gate != null) gate.release();
    }
  }

  /** Quem está enviando: a conta (pelo login) ou, sem login, a rede. */
  private static String uploadKey(HttpServletRequest req, String ip) {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      try { String u = br.com.personalfighters.service.impl.AuthenticationService.getUsernameFromToken(h.substring(7).trim()); if (u != null) return "u|" + u; }
      catch (Exception ignored) { }
    }
    return "ip|" + ip;
  }

  /** Teto do servidor inteiro: no máximo 400 MB de envios por hora (o banco não enche entre uma conferência e outra). */
  static final long HOURLY_UPLOAD_BYTES_ALL = 400L * 1024 * 1024;
  private final long[] global = {0, 0};

  private boolean spendUpload(String key, long bytes) {
    long now = System.currentTimeMillis();
    synchronized (global) {
      if (now - global[0] > HOUR) { global[0] = now; global[1] = 0; }
      if (global[1] + bytes > HOURLY_UPLOAD_BYTES_ALL) return false;
    }
    long[] w = uploads.computeIfAbsent(key, k -> new long[]{now, 0});
    synchronized (w) {
      if (now - w[0] > 24 * HOUR) { w[0] = now; w[1] = 0; }
      if (w[1] + bytes > DAILY_UPLOAD_BYTES) return false;
      w[1] += bytes;
    }
    synchronized (global) { global[1] += bytes; }
    return true;
  }

  private static void tooBig(HttpServletResponse res) throws IOException {
    res.setStatus(413);
    res.setContentType("application/json;charset=UTF-8");
    res.getWriter().write("{\"message\":\"O arquivo é grande demais. Tente uma foto ou vídeo menor.\"}");
  }

  /** Corta a leitura quando passa do limite (envio sem tamanho declarado). */
  private static final class LimitedRequest extends javax.servlet.http.HttpServletRequestWrapper {
    private final long limit;
    LimitedRequest(HttpServletRequest r, long limit) { super(r); this.limit = limit; }
    @Override public javax.servlet.ServletInputStream getInputStream() throws IOException {
      javax.servlet.ServletInputStream in = super.getInputStream();
      return new javax.servlet.ServletInputStream() {
        long n = 0;
        private void count(long k) throws IOException { n += k; if (n > limit) throw new IOException("Envio grande demais"); }
        @Override public int read() throws IOException { int b = in.read(); if (b >= 0) count(1); return b; }
        @Override public int read(byte[] b, int off, int len) throws IOException { int k = in.read(b, off, len); if (k > 0) count(k); return k; }
        @Override public boolean isFinished() { return in.isFinished(); }
        @Override public boolean isReady() { return in.isReady(); }
        @Override public void setReadListener(javax.servlet.ReadListener l) { in.setReadListener(l); }
      };
    }
    @Override public java.io.BufferedReader getReader() throws IOException {
      return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding()));
    }
  }

  private static void headers(String path, HttpServletResponse res) {
    if (isMedia(path)) {
      // Foto/vídeo/áudio enviado por usuário: nunca executa nada, mesmo aberto direto no navegador
      res.setHeader("Content-Security-Policy", "default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'; sandbox");
      res.setHeader("X-Content-Type-Options", "nosniff");
    } else if (path.startsWith("/painel") || path.startsWith("/admin/ui")) {
      res.setHeader("Content-Security-Policy",
          "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; " +
          "img-src 'self' data: blob: https://*.tile.openstreetmap.org; media-src 'self' blob:; connect-src 'self'; " +
          "frame-src 'self'; frame-ancestors 'self'; object-src 'none'; base-uri 'self'; form-action 'self'");
      res.setHeader("Referrer-Policy", "no-referrer");
    }
  }

  private static boolean isUpload(HttpServletRequest r) {
    String ct = r.getContentType();
    return ct != null && ct.toLowerCase().startsWith("multipart/");
  }

  private static boolean isMedia(String p) {
    return p.startsWith("/orders/photos/") || p.startsWith("/posts/photos/") || p.startsWith("/posts/videos/")
        || p.startsWith("/chat/image/") || (p.startsWith("/users/") && p.endsWith("/avatar")) || p.contains("/audio") || p.contains("/certificates/") || p.contains("/photo")
        || (p.contains("/showcase/") && (p.endsWith("/cover") || p.endsWith("/image") || p.endsWith("/cover-image")));
  }

  /** IP real do aparelho (o servidor fica atrás do Cloudflare/Render). */
  public static String clientIp(HttpServletRequest r) {
    String cf = r.getHeader("CF-Connecting-IP");
    if (cf != null && !cf.isBlank()) return cf.trim();
    String xf = r.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
    return r.getRemoteAddr();
  }

  private boolean allow(String ip, String bucket, int max, long windowMs) {
    long now = System.currentTimeMillis();
    Window w = hits.computeIfAbsent(bucket + "|" + ip, k -> new Window());
    synchronized (w) {
      if (now - w.start > windowMs) { w.start = now; w.count = 0; }
      w.count++;
      return w.count <= max;
    }
  }

  /** Limpa contadores velhos a cada 10 minutos. */
  @Scheduled(fixedDelay = 600_000)
  public void cleanup() {
    long now = System.currentTimeMillis();
    hits.entrySet().removeIf(e -> now - e.getValue().start > 2 * HOUR);
    uploads.entrySet().removeIf(e -> now - e.getValue()[0] > 24 * HOUR);
  }
}
