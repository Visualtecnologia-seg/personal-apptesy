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

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String path = req.getRequestURI();
    String m = req.getMethod();
    String ip = clientIp(req);

    // Endereço da API com barra no fim ("/x/") ou caracteres de desvio: recusa (evita furar travas por variação do caminho)
    if (path.length() > 1 && path.endsWith("/") && !(path.startsWith("/painel/") || path.startsWith("/app/") || path.startsWith("/static/"))
        || path.contains("//") || path.contains("\\") || path.contains("%2f") || path.contains("%2F") || path.contains("%5c") || path.contains("%5C")) {
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
        || (p.startsWith("/users/") && p.endsWith("/avatar")) || p.contains("/audio") || p.contains("/certificates/") || p.contains("/photo")
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
  }
}
