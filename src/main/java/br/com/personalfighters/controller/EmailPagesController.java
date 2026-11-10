package br.com.personalfighters.controller;

import br.com.personalfighters.service.EmailAutomationService;
import br.com.personalfighters.util.MailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Páginas abertas pelos links dos e-mails (sem login):
 *  - /email/sair: parar (ou voltar a) receber lembretes e resumos. Códigos e avisos de segurança continuam.
 *  - /abrir/{cliente|profissional}: abre o app no celular; no computador explica como abrir.
 */
@RestController
@RequiredArgsConstructor
public class EmailPagesController {

  private final EmailAutomationService automation;

  private static ResponseEntity<String> page(String title, String body) {
    String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<meta name=\"robots\" content=\"noindex\"><title>" + MailTemplate.esc(title) + "</title><style>" +
        "body{margin:0;background:#EEF1F6;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1F2937}" +
        ".c{max-width:440px;margin:40px auto;background:#fff;border-radius:18px;overflow:hidden}" +
        ".h{background:#0B1530;padding:18px 24px;display:flex;align-items:center;gap:12px;color:#fff;font-weight:700;letter-spacing:4px;font-size:14px}" +
        ".h span{background:#fff;border-radius:9px;width:36px;height:36px;display:grid;place-items:center}.h img{width:26px}" +
        ".b{padding:28px 24px}h1{font-size:22px;color:#0B1530;margin:0 0 12px}p{line-height:1.55;margin:0 0 16px}" +
        "a.btn,button{display:block;width:100%;box-sizing:border-box;text-align:center;padding:15px;border-radius:12px;border:0;background:#0B1530;color:#fff;font-size:16px;font-weight:700;text-decoration:none;cursor:pointer;margin-bottom:10px}" +
        ".sec{background:#fff;color:#0B1530;border:1px solid #CBD3E1}small{color:#6B7280}</style></head><body><div class=\"c\">" +
        "<div class=\"h\"><span><img src=\"/email/logo.png\" alt=\"\"></span>OPUS</div><div class=\"b\">" + body + "</div></div></body></html>";
    return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
        .header("Cache-Control", "no-store").header("X-Robots-Tag", "noindex").body(html);
  }

  private static ResponseEntity<String> invalid() {
    return page("Link inválido", "<h1>Link inválido</h1><p>Este link não está completo. Abra de novo pelo botão no fim do e-mail.</p>");
  }

  /** Abrir o link só mostra a confirmação (programas que conferem links não tiram a pessoa da lista sem querer). */
  @GetMapping("/email/sair")
  public ResponseEntity<String> confirm(@RequestParam(required = false) Long u, @RequestParam(required = false) String t) {
    if (u == null || !automation.tokenOk(u, t)) return invalid();
    String q = "u=" + u + "&t=" + MailTemplate.esc(t);
    return page("Parar de receber e-mails", "<h1>Parar de receber estes e-mails?</h1>" +
        "<p>Você deixa de receber lembretes, novidades e resumos do OPUS. Códigos e avisos de segurança da sua conta continuam chegando.</p>" +
        "<form method=\"post\" action=\"/email/sair?" + q + "\"><button type=\"submit\">Sim, parar de receber</button></form>" +
        "<small>Mudou de ideia? É só fechar esta página.</small>");
  }

  @PostMapping("/email/sair")
  public ResponseEntity<String> unsubscribe(@RequestParam(required = false) Long u, @RequestParam(required = false) String t) {
    if (u == null || !automation.tokenOk(u, t)) return invalid();
    automation.setMarketing(u, false);
    String q = "u=" + u + "&t=" + MailTemplate.esc(t);
    return page("Pronto", "<h1>Pronto, você saiu da lista</h1><p>Não vamos mais mandar lembretes nem resumos para o seu e-mail.</p>" +
        "<form method=\"post\" action=\"/email/voltar?" + q + "\"><button type=\"submit\" class=\"sec\">Quero voltar a receber</button></form>");
  }

  @PostMapping("/email/voltar")
  public ResponseEntity<String> resubscribe(@RequestParam(required = false) Long u, @RequestParam(required = false) String t) {
    if (u == null || !automation.tokenOk(u, t)) return invalid();
    automation.setMarketing(u, true);
    return page("Pronto", "<h1>Que bom, você voltou!</h1><p>Você vai receber de novo nossos lembretes e resumos.</p>");
  }

  /** Abre o app pelo link do e-mail. */
  @GetMapping("/abrir/{app}")
  public ResponseEntity<String> open(@PathVariable String app, @RequestParam(required = false) String p) {
    boolean pro = "profissional".equals(app);
    if (!pro && !"cliente".equals(app)) return invalid();
    String path = p == null ? "" : p.trim();
    if (!path.matches("[A-Za-z0-9/_-]{0,80}")) path = "";
    String deep = (pro ? "opus-profissional://" : "opus-cliente://") + path;
    String name = pro ? "OPUS Profissional" : "OPUS";
    return page("Abrir o " + name,
        "<h1>Abrindo o " + name + "…</h1><p>Se o app não abrir sozinho, toque no botão abaixo no celular onde o " + name + " está instalado.</p>" +
            "<a class=\"btn\" href=\"" + MailTemplate.esc(deep) + "\">Abrir o app</a>" +
            "<small>Está no computador? Abra este e-mail no celular.</small>" +
            "<script>setTimeout(function(){location.href=" + jsString(deep) + "},300)</script>");
  }

  private static String jsString(String s) {
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c") + "\"";
  }
}
