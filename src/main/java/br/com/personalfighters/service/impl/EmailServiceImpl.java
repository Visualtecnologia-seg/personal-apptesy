package br.com.personalfighters.service.impl;

import br.com.personalfighters.service.EmailService;
import br.com.personalfighters.util.MailTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

  private final JavaMailSender emailSender;
  /** Envio em segundo plano: quem pediu o e-mail não fica esperando o servidor de e-mail responder. */
  private final ExecutorService pool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500),
      r -> { Thread t = new Thread(r, "email"); t.setDaemon(true); return t; }, new ThreadPoolExecutor.DiscardOldestPolicy());

  public EmailServiceImpl(JavaMailSender emailSender) { this.emailSender = emailSender; }

  private static String first(String name) {
    if (name == null || name.isBlank()) return "";
    return name.trim().split("\\s+")[0];
  }

  private static String hello(String name) {
    String f = first(name);
    return f.isEmpty() ? "Olá!" : "Olá, " + f + "!";
  }

  @Override
  public void sendNewPassword(String email, String password, String name) {
    send(email, "Sua senha provisória do OPUS", MailTemplate.client()
        .preheader("Use esta senha para entrar e troque logo em seguida.")
        .kicker("Acesso").title("Sua senha provisória")
        .p(hello(name) + " Use a senha abaixo para entrar no app:")
        .alert(password, false)
        .p("Depois de entrar, troque por uma senha só sua em Perfil → Segurança.")
        .small("Se não foi você que pediu, fale com o suporte do OPUS."));
  }

  @Override
  public void sendWelcome(String email, String name) { sendWelcome(email, name, false); }

  @Override
  public void sendWelcome(String email, String name, boolean pro) {
    if (pro) {
      send(email, "Bem-vindo ao OPUS, " + first(name) + "!", MailTemplate.pro()
          .preheader("Sua conta de profissional foi criada. Veja como receber os primeiros pedidos.")
          .hero("ganhos.jpg").kicker("Conta criada").title("Bem-vindo ao OPUS, " + first(name) + "!")
          .p("Sua conta de profissional está pronta. Agora faltam poucos passos para os clientes encontrarem você:")
          .checklist(Arrays.asList(
              "**Envie seus documentos** para conferirmos sua identidade. Profissional verificado aparece primeiro.",
              "**Cadastre seus serviços** com preço e uma descrição curta do que você faz.",
              "**Defina onde receber**: chave PIX no seu CPF ou CNPJ, ou conta bancária.",
              "**Coloque uma foto** sua no perfil. Perfis com foto recebem mais pedidos."))
          .button("Abrir o app", MailTemplate.openApp(true, null))
          .p("O cliente paga pelo app e o dinheiro fica guardado. Você recebe assim que o serviço termina, sem precisar cobrar ninguém.")
          .small("Dúvidas? Responda este e-mail que a gente ajuda."));
    } else {
      send(email, "Bem-vindo ao OPUS, " + first(name) + "!", MailTemplate.client()
          .preheader("Sua conta foi criada. Aulas e serviços com profissionais verificados.")
          .hero("boxe.jpg").kicker("Conta criada").title("Bem-vindo ao OPUS, " + first(name) + "!")
          .p("Que bom ter você aqui. No OPUS você encontra aulas e serviços com profissionais de identidade conferida, perto de você.")
          .checklist(Arrays.asList(
              "**Escolha o que precisa**: personal, luta, alongamento, eletricista, encanador, jardinagem e mais.",
              "**Combine dia e horário** direto pelo app.",
              "**Pague pelo app** com PIX. O profissional só recebe depois que o serviço termina."))
          .button("Encontrar um profissional", MailTemplate.openApp(false, null))
          .small("Dúvidas? Responda este e-mail que a gente ajuda."));
    }
  }

  @Override
  public void sendOrderConfirmed(String email, String name, String date, String time) {
    send(email, "Pedido confirmado para " + date, MailTemplate.client()
        .preheader("Seu pedido está confirmado para " + date + " às " + time + ".")
        .hero("relogio.jpg").kicker("Pedido confirmado").title("Está tudo certo!")
        .p(hello(name) + " Seu pedido foi confirmado.")
        .details(MailTemplate.rows("Dia", date, "Horário", time))
        .p("Você recebe um aviso no app quando o profissional estiver a caminho.")
        .button("Ver meu pedido", MailTemplate.openApp(false, "pedidos")));
  }

  @Override
  public void sendOrderCancelled(String email, String name) {
    send(email, "Seu pedido foi cancelado", MailTemplate.client()
        .preheader("O pedido foi cancelado. Se você já pagou, o valor volta para você.")
        .kicker("Pedido cancelado").title("Seu pedido foi cancelado")
        .p(hello(name) + " Avisamos que o seu pedido foi cancelado.")
        .alert("Se você já tinha pago, o valor é devolvido automaticamente.", false)
        .p("Quer marcar com outro profissional? Tem muita gente boa esperando por você.")
        .button("Procurar outro profissional", MailTemplate.openApp(false, null)));
  }

  @Override
  public void sendPayoutCode(String email, String name, String code) {
    send(email, "Código do saque: " + code, MailTemplate.pro()
        .preheader("Seu código para confirmar o saque é " + code + ".")
        .kicker("Saque").title("Confirme seu saque")
        .p(hello(name) + " Recebemos seu pedido de saque. Digite este código no app:")
        .code(code, "Vale por 15 minutos")
        .alert("Não pediu um saque? Não passe este código para ninguém e fale com o suporte agora.", true));
  }

  @Override
  public void sendResetCode(String email, String name, String code) {
    send(email, "Código para criar uma nova senha: " + code, MailTemplate.client()
        .preheader("Seu código é " + code + ". Vale por 15 minutos.")
        .kicker("Nova senha").title("Criar uma nova senha")
        .p(hello(name) + " Use este código no app para criar sua nova senha:")
        .code(code, "Vale por 15 minutos")
        .p("Sua senha atual continua valendo até você criar a nova.")
        .small("Se não foi você que pediu, pode ignorar este e-mail. Ninguém do OPUS vai pedir este código."));
  }

  @Override
  public void sendEmailCode(String email, String code) {
    send(email, "Seu código OPUS: " + code, MailTemplate.client()
        .preheader("Seu código é " + code + ". Vale por 10 minutos.")
        .kicker("Confirmar e-mail").title("Seu código de confirmação")
        .p("Olá! Digite este código no app para confirmar seu e-mail:")
        .code(code, "Vale por 10 minutos")
        .small("Se não foi você, pode ignorar este e-mail."));
  }

  @Override
  public void sendSecurityAlert(String email, String name, String subject, String title, String text) {
    send(email, subject, MailTemplate.client()
        .preheader(text)
        .kicker("Segurança da conta").title(title)
        .p(hello(name))
        .alert(text, true)
        .button("Abrir o app", MailTemplate.openApp(false, null))
        .small("Este é um aviso automático para proteger sua conta. Nunca pedimos sua senha ou códigos."));
  }

  @Override
  public void sendText(String to, String subject, String text) {
    MailTemplate t = MailTemplate.client().title(subject);
    for (String para : text.split("\n\n")) {
      String s = para.trim();
      if (s.isEmpty() || s.equals("Equipe OPUS")) continue;
      t.p(s);
    }
    send(to, subject, t);
  }

  @Override
  public boolean isConfigured() {
    String u = System.getenv("MAIL_USERNAME"), p = System.getenv("MAIL_PASSWORD");
    return u != null && !u.isBlank() && p != null && !p.isBlank();
  }

  @Override
  public void send(String to, String subject, MailTemplate t) {
    if (to == null || !to.contains("@")) return;
    final String html = t.html(), text = t.text();
    pool.execute(() -> deliver(to, subject, html, text, null, null, null));
  }

  @Override
  public void send(String to, String subject, MailTemplate t, String fileName, byte[] file, String mime) {
    if (to == null || !to.contains("@")) return;
    final String html = t.html(), text = t.text();
    pool.execute(() -> deliver(to, subject, html, text, fileName, file, mime));
  }

  private void deliver(String to, String subject, String html, String text, String fileName, byte[] file, String mime) {
    try {
      MimeMessage msg = emailSender.createMimeMessage();
      MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
      // Remetente: MAIL_FROM (provedor de e-mail) ou a própria conta
      String from = System.getenv("MAIL_FROM");
      if (from == null || from.isBlank()) from = System.getenv("MAIL_USERNAME");
      if (from != null && from.contains("@")) {
        h.setFrom(new InternetAddress(from.trim(), "OPUS", "UTF-8"));
        h.setReplyTo(from.trim());
      }
      h.setTo(to);
      h.setSubject(subject);
      h.setText(text, html);
      if (fileName != null && file != null) h.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(file), mime);
      emailSender.send(msg);
    } catch (Exception e) {
      log.error("Falha ao enviar e-mail para {}: {}", to, e.getMessage());
    }
  }
}
