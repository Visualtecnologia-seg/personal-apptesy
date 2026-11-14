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
        || ("POST".equals(m) && (path.equals("/auth/google") || path.equals("/auth/apple")) && !allow(ip, "google", 30, HOUR))
        || ("POST".equals(m) && (path.equals("/auth") || path.equals("/panel-auth/login") || path.equals("/panel-auth/2fa")) && !allow(ip, "login", 30, 10 * MIN))
        || (path.startsWith("/search") && !allow(ip, "search", 120, MIN))
        || ("POST".equals(m) && path.equals("/messages") && !allow(ip, "msg", 60, MIN))
        || ("POST".equals(m) && path.equals("/partners/lead") && !allow(ip, "lead", 5, HOUR))
        || ("POST".equals(m) && path.startsWith("/showcase/") && !allow(ip, "showclick", 30, MIN))
        || (("PUT".equals(m) || "POST".equals(m)) && path.startsWith("/pro/showcase") && !allow(ip, "showedit", 30, 10 * MIN))
        || ("POST".equals(m) && isUpload(req) && !allow(ip, "upload", 40, 10 * MIN))) {
      res.setStatus(429);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"message\":\"Muitas tentativas seguidas. Espere um pouco e tente de novo.\"}");
      return;
    }

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
    chain.doFilter(req, res);
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
  private static String clientIp(HttpServletRequest r) {
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
