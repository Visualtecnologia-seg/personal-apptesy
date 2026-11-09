package br.com.personalfighters.util;

/**
 * Regra de senha forte (contas novas e senhas trocadas):
 * no mínimo 6 caracteres, com pelo menos 1 letra maiúscula, 1 número e 1 caractere especial.
 */
public final class PasswordRules {
  private PasswordRules() { }

  /** Devolve a mensagem do problema, ou null quando a senha serve. */
  public static String check(String p) {
    if (p == null || p.length() < 6) return "A senha precisa ter pelo menos 6 caracteres.";
    if (p.length() > 100) return "A senha está grande demais.";
    if (!p.matches(".*[A-ZÀ-Ý].*")) return "Use pelo menos uma letra maiúscula na senha.";
    if (!p.matches(".*\\d.*")) return "Use pelo menos um número na senha.";
    if (!p.matches(".*[^A-Za-zÀ-ÿ0-9\\s].*")) return "Use pelo menos um caractere especial na senha (ex.: ! @ # $ %).";
    return null;
  }
}
