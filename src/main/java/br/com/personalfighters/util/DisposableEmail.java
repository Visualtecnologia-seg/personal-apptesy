package br.com.personalfighters.util;

import java.util.Set;

/** E-mails descartáveis ("de 10 minutos"), usados para criar contas falsas. */
public final class DisposableEmail {
  private DisposableEmail() { }

  private static final Set<String> DOMAINS = Set.of(
      "10minutemail.com", "10minutemail.net", "10minemail.com", "20minutemail.com", "guerrillamail.com", "guerrillamail.net", "guerrillamail.org",
      "guerrillamail.biz", "guerrillamailblock.com", "sharklasers.com", "grr.la", "pokemail.net", "spam4.me", "mailinator.com", "mailinator.net",
      "mailinator2.com", "notmailinator.com", "reallymymail.com", "tempmail.com", "temp-mail.org", "temp-mail.io", "tempmail.net", "tempmailo.com",
      "tempmail.dev", "tempmailaddress.com", "tempr.email", "tempinbox.com", "throwawaymail.com", "trashmail.com", "trashmail.net", "trashmail.de",
      "yopmail.com", "yopmail.net", "yopmail.fr", "cool.fr.nf", "jetable.org", "getnada.com", "nada.email", "dispostable.com", "maildrop.cc",
      "mailnesia.com", "mintemail.com", "mohmal.com", "emailondeck.com", "fakeinbox.com", "fakemail.net", "spamgourmet.com", "mytemp.email",
      "moakt.com", "emailfake.com", "email-fake.com", "fakermail.com", "burnermail.io", "33mail.com", "inboxkitten.com", "mail.tm", "mail.gw",
      "1secmail.com", "1secmail.net", "1secmail.org", "esiix.com", "wwjmp.com", "xojxe.com", "yoggm.com", "dropmail.me", "10mail.org",
      "emltmp.com", "spymail.one", "tmpmail.org", "tmpmail.net", "tmail.ws", "linshiyouxiang.net", "byom.de", "trbvm.com", "dcctb.com",
      "mailpoof.com", "harakirimail.com", "discard.email", "discardmail.com", "spambox.us", "incognitomail.org", "anonbox.net", "mvrht.net",
      "tempail.com", "fexpost.com", "fexbox.org", "rover.info", "vomoto.com", "kzccv.com", "luxusmail.org", "minuteinbox.com", "crazymailing.com");

  public static boolean is(String email) {
    if (email == null || !email.contains("@")) return false;
    String d = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
    if (DOMAINS.contains(d)) return true;
    for (String x : DOMAINS) if (d.endsWith("." + x)) return true;
    return false;
  }
}
