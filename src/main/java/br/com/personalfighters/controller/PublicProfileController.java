package br.com.personalfighters.controller;

import br.com.personalfighters.util.MailTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Página pública do profissional (/p/{id}), usada no "Compartilhar".
 * No WhatsApp/Instagram o link vira um cartão com foto, nome, nota e serviços.
 * Mostra só o que já é público no app (nada de contato, documento ou endereço).
 */
@RestController
@RequiredArgsConstructor
public class PublicProfileController {

  private final JdbcTemplate jdbc;

  @Value("${opus.download.cliente:https://expo.dev/artifacts/eas/y-z3b3YW34wIzoyxnrngeULPGsvRhrVAmGENc2PSQ1M.apk}")
  private String downloadUrl;

  @GetMapping("/p/{id}")
  public ResponseEntity<String> page(@PathVariable String id) {
    long uid;
    try { uid = Long.parseLong(id); } catch (Exception e) { return notFound(); }
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT u.id, u.name, u.surname, u.avatar_url, COALESCE(u.verified, FALSE) AS verified, p.about, p.years_experience, " +
            "(SELECT AVG(r.rating) FROM review_ r WHERE r.professional_id = u.id AND COALESCE(r.type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO') AS rating, " +
            "(SELECT COUNT(*) FROM review_ r WHERE r.professional_id = u.id AND COALESCE(r.type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO') AS reviews, " +
            "(SELECT COUNT(*) FROM order_ o WHERE o.professional_id = u.id AND o.status IN ('DONE','REVIEWED')) AS done " +
            "FROM user_ u JOIN professional_ p ON p.id = u.professional_id " +
            "WHERE u.id = ? AND COALESCE(u.active, TRUE) AND u.deleted_at IS NULL AND NOT COALESCE(u.blocked, FALSE) " +
            "AND COALESCE(u.signup_app, 'PRO') <> 'CLIENT' AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'PROFESSIONAL')", uid);
    if (rows.isEmpty()) return notFound();
    Map<String, Object> r = rows.get(0);
    List<String> services = jdbc.queryForList(
        "SELECT p.name FROM product__professional pp JOIN product_ p ON p.id = pp.product_id WHERE pp.professional_id = ? AND COALESCE(p.active, TRUE) ORDER BY p.name LIMIT 6",
        String.class, uid);
    List<Map<String, Object>> praise = ReviewController.topTags(jdbc, uid, 5);

    String base = MailTemplate.base();
    String first = Objects.toString(r.get("name"), "").trim();
    String sur = Objects.toString(r.get("surname"), "").trim();
    String name = (first + (sur.isEmpty() ? "" : " " + sur)).trim();
    if (name.isEmpty()) name = "Profissional";
    boolean hasPhoto = r.get("avatar_url") != null && !Objects.toString(r.get("avatar_url")).isBlank();
    String img = hasPhoto ? base + "/users/" + uid + "/avatar" : base + "/email/logo.png";
    long reviews = ((Number) r.get("reviews")).longValue();
    long done = ((Number) r.get("done")).longValue();
    String rating = r.get("rating") == null ? null : String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", ((Number) r.get("rating")).doubleValue());
    String svc = services.isEmpty() ? "Profissional no OPUS" : String.join(" · ", services.subList(0, Math.min(3, services.size())));
    String desc = (rating != null ? "★ " + rating + " (" + reviews + " avaliaç" + (reviews == 1 ? "ão" : "ões") + ") · " : "") + svc + ". Contrate pelo app OPUS com pagamento protegido.";
    String deep = "opus-cliente://professionals/" + uid;
    String url = base + "/p/" + uid;
    boolean verified = Boolean.TRUE.equals(r.get("verified"));
    String about = Objects.toString(r.get("about"), "").trim();
    if (about.length() > 220) about = about.substring(0, 217) + "…";

    StringBuilder pr = new StringBuilder();
    int pn = 0;
    for (Map<String, Object> t : praise) {
      if (pn++ >= 3) break;
      pr.append("<span class=\"pz\">👍 ").append(MailTemplate.esc(Objects.toString(t.get("tag")))).append("</span>");
    }
    String firstName = name.split(" ")[0];
    // Capa e indicação em destaque (só o que o administrador aprovou)
    Map<String, Object> show = ShowcaseController.publicData(jdbc, uid);
    @SuppressWarnings("unchecked") Map<String, Object> cov = (Map<String, Object>) show.get("cover");
    @SuppressWarnings("unchecked") List<Map<String, Object>> recs = (List<Map<String, Object>>) show.get("items");
    String heroStyle = cov == null ? "" : " style=\"background:linear-gradient(180deg,rgba(11,21,48,.35),rgba(11,21,48,.82)),url('/showcase/" + uid + "/cover?v=" + cov.get("v") + "') center/cover\"";
    String partnerTag = cov != null && "PARTNER".equals(cov.get("mode")) && cov.get("brand") != null
        ? "<div class=\"ptag\">Parceria · " + MailTemplate.esc(Objects.toString(cov.get("brand"))) + "</div>" : "";
    String recoHtml = "";
    if (!recs.isEmpty()) {
      Map<String, Object> it = recs.get(0);
      recoHtml = "<div class=\"sec\"><a class=\"reco\" href=\"/showcase/items/" + it.get("id") + "/go\" rel=\"sponsored nofollow noopener\" target=\"_blank\">" +
          "<img src=\"/showcase/items/" + it.get("id") + "/image?v=" + it.get("v") + "\" alt=\"\">" +
          "<div><small>" + ("PARTNER".equals(it.get("kind")) ? "PARCERIA · " : "") + MailTemplate.esc(firstName.toUpperCase()) + " RECOMENDA</small><b>" + MailTemplate.esc(Objects.toString(it.get("title"))) + "</b>" +
          "<span>Ver " + ("Mercado Livre".equals(it.get("store")) ? "no " : "na ") + MailTemplate.esc(Objects.toString(it.get("store"))) + " ↗</span></div></a></div>";
    }
    // Um depoimento real de cliente (o mais recente, nota 4 ou 5, com comentário)
    String quote = "";
    try {
      List<Map<String, Object>> q = jdbc.queryForList("SELECT r.comment, r.rating, c.name AS who FROM review_ r LEFT JOIN user_ c ON c.id = r.customer_id " +
          "WHERE r.professional_id = ? AND COALESCE(r.type,'CLIENT_TO_PRO') = 'CLIENT_TO_PRO' AND r.rating >= 4 AND LENGTH(TRIM(COALESCE(r.comment,''))) >= 12 ORDER BY r.id DESC LIMIT 1", uid);
      if (!q.isEmpty()) {
        String c = Objects.toString(q.get(0).get("comment"), "").trim();
        if (c.length() > 160) c = c.substring(0, 157) + "…";
        String who = Objects.toString(q.get(0).get("who"), "Cliente").trim().split(" ")[0];
        quote = "<div class=\"sec\"><div class=\"q\"><div class=\"qs\">★★★★★</div><p>“" + MailTemplate.esc(c) + "”</p><span>" + MailTemplate.esc(who) + ", cliente pelo OPUS</span></div></div>";
      }
    } catch (Exception ignored) { }
    String fn = MailTemplate.esc(firstName);
    String initials = name.substring(0, 1).toUpperCase();
    String meta = (rating != null ? "<b>★ " + rating + "</b> · " + reviews + " avaliaç" + (reviews == 1 ? "ão" : "ões") + " · " : "<b>Novo no OPUS</b> · ")
        + MailTemplate.esc(services.isEmpty() ? "Profissional" : services.get(0));
    // ícones simples desenhados em SVG (sem depender de nada de fora)
    String icShield = "<svg viewBox=\"0 0 24 24\" width=\"30\" height=\"30\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5l8-3z\"/><path d=\"M8.5 12l2.5 2.5 4.5-5\"/></svg>";
    String icBolt = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"currentColor\"><path d=\"M13 2L4 14h7l-1 8 9-12h-7l1-8z\"/></svg>";
    String icLock = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.2\" stroke-linecap=\"round\"><rect x=\"5\" y=\"11\" width=\"14\" height=\"10\" rx=\"2.5\"/><path d=\"M8 11V8a4 4 0 018 0v3\"/></svg>";
    String icCheck = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M20 6L9 17l-5-5\"/></svg>";
    String icId = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"14\" rx=\"2.5\"/><circle cx=\"9\" cy=\"11\" r=\"2.2\"/><path d=\"M6 16c.8-1.5 2-2.2 3-2.2s2.2.7 3 2.2M15 10h3M15 13.5h3\"/></svg>";
    String step = "<div class=\"st\"><div class=\"sti\">%s</div><span>%s</span></div>";
    String flow = String.format(step, "💳", "Você paga<br>pelo app") + "<div class=\"ar\">›</div>" + String.format(step, "🔒", "Fica no<br>cofre") + "<div class=\"ar\">›</div>"
        + String.format(step, "👍", "Você<br>aprova") + "<div class=\"ar\">›</div>" + String.format(step, "💸", fn + "<br>recebe");

    String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
        "<title>" + MailTemplate.esc(name) + " no OPUS</title>" +
        "<meta name=\"description\" content=\"" + MailTemplate.esc(desc) + "\">" +
        "<meta property=\"og:type\" content=\"profile\"><meta property=\"og:site_name\" content=\"OPUS\">" +
        "<meta property=\"og:title\" content=\"" + MailTemplate.esc(name + " no OPUS") + "\">" +
        "<meta property=\"og:description\" content=\"" + MailTemplate.esc(desc) + "\">" +
        "<meta property=\"og:image\" content=\"" + MailTemplate.esc(img) + "\"><meta property=\"og:url\" content=\"" + MailTemplate.esc(url) + "\">" +
        "<meta name=\"twitter:card\" content=\"summary\"><meta name=\"theme-color\" content=\"#0B1F4D\">" +
        "<style>*{box-sizing:border-box}body{margin:0;background:#F3F5F9;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#0B1530;-webkit-font-smoothing:antialiased}" +
        ".w{max-width:480px;margin:0 auto;padding-bottom:110px}" +
        ".hero{background:linear-gradient(150deg,#0B1F4D 0%,#1D4ED8 65%,#0891B2 100%);color:#fff;padding:22px 20px 30px;text-align:center;border-radius:0 0 30px 30px}" +
        ".brand{display:flex;align-items:center;gap:8px;font-weight:800;letter-spacing:4px;font-size:13px;opacity:.9}.brand img{width:22px;height:22px;background:#fff;border-radius:7px;padding:3px}" +
        ".av{width:96px;height:96px;border-radius:50%;border:4px solid rgba(255,255,255,.9);margin:18px auto 12px;object-fit:cover;background:#DBEAFE;color:#1D4ED8;display:grid;place-items:center;font-size:38px;font-weight:800}" +
        "h1{font-size:26px;margin:0}.meta{margin:6px 0 0;font-size:14.5px;color:#DBEAFE}.meta b{color:#FDE68A}" +
        ".ver{display:inline-flex;align-items:center;gap:6px;margin-top:10px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.25);padding:6px 12px;border-radius:99px;font-size:12.5px;font-weight:700}" +
        ".pzs{margin-top:14px}.pz{display:inline-block;background:rgba(255,255,255,.95);color:#92400E;font-weight:700;font-size:12.5px;padding:6px 11px;border-radius:99px;margin:3px}" +
        ".sec{padding:26px 18px 0}.h2{font-size:20px;font-weight:800;margin:0 0 14px;text-align:center}" +
        ".safe{background:#fff;border-radius:24px;padding:22px 18px;box-shadow:0 8px 28px rgba(11,31,77,.10);border:2px solid #BBF7D0;text-align:center}" +
        ".safe .ic{width:60px;height:60px;border-radius:20px;background:#16A34A;color:#fff;display:grid;place-items:center;margin:0 auto 10px}" +
        ".safe h3{margin:0;font-size:21px;color:#065F46}.safe p{margin:8px 0 0;font-size:14.5px;line-height:1.5;color:#334155}" +
        ".flow{display:flex;align-items:flex-start;justify-content:center;gap:2px;margin-top:18px}.st{flex:1;display:flex;flex-direction:column;align-items:center;gap:6px;font-size:11.5px;font-weight:700;color:#0B1530;line-height:1.25}" +
        ".sti{width:44px;height:44px;border-radius:50%;background:#ECFDF5;display:grid;place-items:center;font-size:20px}.ar{color:#94A3B8;font-size:22px;margin-top:8px}" +
        ".feats{display:grid;gap:10px;margin-top:12px}.f{display:flex;align-items:center;gap:14px;background:#fff;border-radius:20px;padding:16px;border:1px solid #E5E7EB}" +
        ".fi{width:46px;height:46px;flex:none;border-radius:15px;display:grid;place-items:center}.f b{display:block;font-size:16px}.f span{font-size:13.5px;color:#475569;line-height:1.4}" +
        ".bar{position:fixed;left:0;right:0;bottom:0;background:rgba(255,255,255,.96);backdrop-filter:blur(8px);border-top:1px solid #E5E7EB;padding:12px 16px calc(12px + env(safe-area-inset-bottom))}" +
        ".bar .in{max-width:480px;margin:0 auto}.cta{display:flex;align-items:center;justify-content:center;gap:8px;height:54px;border-radius:16px;background:#0B1530;color:#fff;font-weight:800;font-size:16.5px;text-decoration:none}" +
        ".dl{display:block;text-align:center;margin-top:8px;font-size:13px;color:#1D4ED8;font-weight:700;text-decoration:none}" +
        ".q{background:#fff;border-radius:22px;padding:18px 20px;border:1px solid #E5E7EB}.qs{color:#F59E0B;letter-spacing:2px;font-size:15px}.q p{margin:8px 0 6px;font-size:15.5px;line-height:1.5;color:#0B1530;font-style:italic}.q span{font-size:12.5px;color:#64748B;font-weight:700}" +
        ".ptag{display:inline-block;margin-top:12px;font-size:11px;font-weight:800;letter-spacing:.8px;text-transform:uppercase;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.3);padding:5px 10px;border-radius:8px}" +
        ".reco{display:flex;gap:14px;align-items:center;background:linear-gradient(135deg,#1A0A10,#3B0A14);color:#fff;border-radius:20px;padding:12px;text-decoration:none}.reco img{width:66px;height:66px;border-radius:14px;object-fit:cover;background:#fff;flex:none}" +
        ".reco small{display:block;font-size:10px;letter-spacing:1.2px;font-weight:800;color:#FDBA74}.reco b{display:block;font-size:16px;margin:3px 0}.reco span{font-size:12.5px;color:#FECACA;font-weight:700}" +
        ".foot{text-align:center;color:#94A3B8;font-size:12px;margin-top:26px}</style></head><body><div class=\"w\">" +
        "<div class=\"hero\"" + heroStyle + "><div class=\"brand\"><img src=\"/email/logo.png\" alt=\"\">OPUS</div>" +
        (hasPhoto ? "<img class=\"av\" src=\"/users/" + uid + "/avatar\" alt=\"\">" : "<div class=\"av\">" + MailTemplate.esc(initials) + "</div>") +
        "<h1>" + MailTemplate.esc(name) + "</h1><p class=\"meta\">" + meta + "</p>" +
        (verified ? "<div class=\"ver\">✓ Identidade verificada pelo OPUS</div>" : "") +
        (pr.length() > 0 ? "<div class=\"pzs\">" + pr + "</div>" : "") + partnerTag +
        "</div>" +
        "<div class=\"sec\"><div class=\"safe\"><div class=\"ic\">" + icShield + "</div>" +
        "<h3>Seu dinheiro protegido</h3>" +
        "<p>Você paga pelo app e o valor fica guardado no cofre do OPUS. " + fn + " só recebe depois que você confere e aprova o serviço.</p>" +
        "<div class=\"flow\">" + flow + "</div></div></div>" +
        quote + recoHtml +
        "<div class=\"sec\"><p class=\"h2\">Rápido, seguro e sem dor de cabeça</p><div class=\"feats\">" +
        "<div class=\"f\"><div class=\"fi\" style=\"background:#FEF3C7;color:#D97706\">" + icBolt + "</div><div><b>Rápido</b><span>Peça em 1 minuto: escreva, fale ou mande uma foto do problema.</span></div></div>" +
        "<div class=\"f\"><div class=\"fi\" style=\"background:#EFF4FF;color:#1D4ED8\">" + icId + "</div><div><b>Seguro</b><span>Profissional com documento e selfie conferidos, chat no app e botão SOS.</span></div></div>" +
        "<div class=\"f\"><div class=\"fi\" style=\"background:#ECFDF5;color:#16A34A\">" + icCheck + "</div><div><b>Resolve de verdade</b><span>Deu problema? O pagamento fica travado e o OPUS resolve com você.</span></div></div>" +
        "</div></div>" +
        "<p class=\"foot\">OPUS · profissionais de confiança perto de você</p></div>" +
        "<div class=\"bar\"><div class=\"in\"><a class=\"cta\" href=\"" + MailTemplate.esc(deep) + "\">Contratar " + fn + " pelo OPUS</a>" +
        "<a class=\"dl\" href=\"" + MailTemplate.esc(downloadUrl) + "\">Ainda não tem o app? Baixe grátis</a></div></div>" +
        "</body></html>";
    return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
        .header("Cache-Control", "public, max-age=600").body(html);
  }

  private static ResponseEntity<String> notFound() {
    String html = "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>OPUS</title></head>" +
        "<body style=\"font-family:sans-serif;background:#EEF1F6;text-align:center;padding:60px 20px;color:#0B1530\"><h1>Perfil não encontrado</h1><p>Este profissional não está mais disponível no OPUS.</p></body></html>";
    return ResponseEntity.status(404).contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(html);
  }
}
