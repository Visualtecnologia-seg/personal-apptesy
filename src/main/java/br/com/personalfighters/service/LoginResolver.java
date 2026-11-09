package br.com.personalfighters.service;

import br.com.personalfighters.util.CpfValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A pessoa pode entrar com usuário, e-mail, CPF ou telefone.
 * Aqui descobrimos de qual conta é (acessos do painel nunca entram por aqui).
 */
@Service
@RequiredArgsConstructor
public class LoginResolver {

  private final JdbcTemplate jdbc;

  /** Devolve o "username" da conta, ou o próprio texto se não achar (para o login falhar normalmente). */
  public String resolve(String login) {
    if (login == null) return null;
    String l = login.trim();
    if (l.isEmpty() || l.toLowerCase().startsWith("painel:")) return l;
    String found = null;
    if (l.contains("@")) {
      found = first("SELECT username FROM user_ WHERE LOWER(email) = LOWER(?) AND username NOT LIKE 'painel:%' ORDER BY id LIMIT 1", l);
    } else {
      String d = l.replaceAll("\\D", "");
      boolean onlyNumbers = l.replaceAll("[\\d\\s().\\-+]", "").isEmpty();
      if (onlyNumbers && d.length() == 11 && CpfValidator.isValid(d)) {
        found = first("SELECT username FROM user_ WHERE regexp_replace(COALESCE(cpf, ''), '[^0-9]', '', 'g') = ? AND username NOT LIKE 'painel:%' LIMIT 1", d);
      }
      if (found == null && onlyNumbers && (d.length() == 11 || d.length() == 13)) {
        String phone = d.length() == 13 && d.startsWith("55") ? d.substring(2) : d;
        found = phoneOwner(phone);
      }
    }
    return found != null ? found : l;
  }

  /** Dono do telefone (prefere a conta que confirmou o número por SMS). */
  public String phoneOwner(String phone) {
    return first("SELECT username FROM user_ WHERE regexp_replace(COALESCE(phone_number, ''), '[^0-9]', '', 'g') IN (?, ?) AND username NOT LIKE 'painel:%' " +
        "ORDER BY phone_verified DESC, id DESC LIMIT 1", phone, "55" + phone);
  }

  private String first(String sql, Object... args) {
    List<String> r = jdbc.queryForList(sql, String.class, args);
    return r.isEmpty() ? null : r.get(0);
  }
}
