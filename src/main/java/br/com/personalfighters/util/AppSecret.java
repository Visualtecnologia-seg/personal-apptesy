package br.com.personalfighters.util;

import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chave mestra do servidor (assina logins, comprovantes de e-mail/celular, links de e-mail e protege as 2 etapas do painel).
 * Se a JWT_SECRET do Render for fraca, curta ou conhecida, usa uma chave forte aleatória criada uma vez e guardada no banco.
 */
public final class AppSecret {
  private AppSecret() { }

  private static volatile String effective;

  public static boolean weak(String s) {
    if (s == null || s.trim().length() < 32) return true;
    String l = s.toLowerCase();
    for (String bad : new String[]{"blueshark", "personal", "fighter", "secret", "segredo", "changeme", "opus-jwt", "teste", "test", "123456", "password", "senha"})
      if (l.contains(bad) && s.length() < 48) return true;
    return s.chars().distinct().count() < 12;
  }

  public static synchronized String effective(String env, JdbcTemplate jdbc) {
    if (effective != null) return effective;
    if (!weak(env)) return effective = env;
    try {
      jdbc.execute("CREATE TABLE IF NOT EXISTS app_secret_ (name VARCHAR(40) PRIMARY KEY, value TEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT NOW())");
      jdbc.update("INSERT INTO app_secret_ (name, value) VALUES ('jwt', ?) ON CONFLICT (name) DO NOTHING", random());
      effective = jdbc.queryForObject("SELECT value FROM app_secret_ WHERE name = 'jwt'", String.class);
    } catch (Exception e) {
      effective = random(); // sem banco: chave só desta execução
    }
    return effective;
  }

  private static String random() {
    byte[] raw = new byte[64];
    new SecureRandom().nextBytes(raw);
    return Base64.getEncoder().encodeToString(raw);
  }
}
