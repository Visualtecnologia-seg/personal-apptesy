package br.com.personalfighters.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Tira dados pessoais de OUTRAS pessoas antes de responder (CPF, chave PIX, telefone, e-mail, documentos).
 * Cada um continua vendo os próprios dados; o administrador vê tudo.
 * Assim o contato fica dentro do app e ninguém consegue a chave PIX ou o CPF de outra conta.
 */
@Component
@RequiredArgsConstructor
public class Redactor {

  private static final Set<String> PRIVATE = new HashSet<>(Arrays.asList(
      "cpf", "pixKey", "pixKeyType", "phoneNumber", "phone", "email", "username", "password",
      "asaasCustomerId", "birthday", "documentUrl", "docFrontUrl", "docBackUrl", "docSelfieUrl",
      "verificationReason", "cardNumber", "creditCards", "bankData",
      "fullAddress", "address", "addresses", "referralCode", "lastLogin", "loginHistory"));

  private final ObjectMapper mapper;

  public static boolean isAdmin() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.getAuthorities().stream().anyMatch(x -> "ROLE_ADMIN".equals(x.getAuthority()));
  }

  /** Converte para JSON e apaga os campos privados de toda pessoa que não seja quem está vendo. */
  public Object strip(Object value, Long viewerId) {
    if (value == null) return null;
    JsonNode tree = mapper.valueToTree(value);
    walk(tree, isAdmin() ? null : viewerId, isAdmin());
    return tree;
  }

  private void walk(JsonNode n, Long viewerId, boolean all) {
    if (n == null) return;
    if (n.isArray()) { for (JsonNode c : (ArrayNode) n) walk(c, viewerId, all); return; }
    if (!n.isObject()) return;
    ObjectNode o = (ObjectNode) n;
    boolean person = o.has("cpf") || o.has("pixKey") || o.has("username") || o.has("phoneNumber");
    boolean me = person && viewerId != null && o.has("id") && o.get("id").canConvertToLong() && o.get("id").asLong() == viewerId;
    if (person && !me && !all) {
      for (String k : PRIVATE) o.remove(k);
      // Localização de outra pessoa só aproximada (~1 km): serve para distância, não mostra a casa.
      for (String k : new String[]{"latitude", "longitude"}) {
        JsonNode v = o.get(k);
        if (v != null && v.isNumber()) o.put(k, Math.round(v.asDouble() * 100.0) / 100.0);
      }
    }
    if (person) o.remove("password"); // nem a própria senha (mesmo criptografada) sai do servidor
    Iterator<String> it = o.fieldNames();
    java.util.List<String> keys = new java.util.ArrayList<>();
    while (it.hasNext()) keys.add(it.next());
    for (String k : keys) walk(o.get(k), viewerId, all);
  }
}
