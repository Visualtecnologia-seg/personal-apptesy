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
    // A criptografia de senha só considera os primeiros 72 bytes: acima disso, senhas diferentes abririam a mesma conta (CVE-2025-22228)
    if (p.length() > 64 || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return "A senha pode ter no máximo 64 caracteres.";
    if (!p.matches(".*[A-ZÀ-Ý].*")) return "Use pelo menos uma letra maiúscula na senha.";
    if (!p.matches(".*\\d.*")) return "Use pelo menos um número na senha.";
    if (!p.matches(".*[^A-Za-zÀ-ÿ0-9\\s].*")) return "Use pelo menos um caractere especial na senha (ex.: ! @ # $ %).";
    return null;
  }
}
