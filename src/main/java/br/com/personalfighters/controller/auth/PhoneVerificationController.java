package br.com.personalfighters.controller.auth;

import br.com.personalfighters.service.CodeStore;
import br.com.personalfighters.service.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Código por SMS (cadastro e "entrar com código").
 * POST /auth/phone/send {phone} → manda o código · POST /auth/phone/verify {phone, code} → comprovante para o cadastro.
 */
@RestController
@RequestMapping("/auth/phone")
@RequiredArgsConstructor
public class PhoneVerificationController {

  private final SmsService sms;
  private final CodeStore codes;

  /** "(11) 98765-4321" → "11987654321" (DDD + celular). Null se não for celular brasileiro. */
  public static String normalize(String raw) {
    String d = raw == null ? "" : raw.replaceAll("\\D", "");
    if (d.startsWith("55") && d.length() == 13) d = d.substring(2);
    if (d.length() != 11 || d.charAt(2) != '9' || d.charAt(0) == '0') return null;
    return d;
  }

  /** SMS de verdade ligado (Twilio configurado). Desligado: o celular não é conferido por SMS. */
  public boolean smsOn() { return !sms.isTestMode(); }

  @PostMapping("/send")
  public ResponseEntity<?> send(@RequestBody Map<String, Object> body) {
    String phone = normalize(Objects.toString(body.get("phone"), ""));
    if (phone == null) return bad("Digite um celular válido com DDD, ex.: (21) 98765-4321.");
    String code = codes.issue("SMS", phone, sms.isTestMode());
    if (code == null) return bad("Espere um pouco para pedir outro código (máximo de 5 por dia).");
    if (!sms.send("+55" + phone, "OPUS: seu código é " + code + ". Não passe este código para ninguém.")) {
      return bad("Não conseguimos enviar o SMS agora. Tente de novo em instantes.");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sent", true);
    out.put("masked", "(" + phone.substring(0, 2) + ") *****-" + phone.substring(7));
    if (sms.isTestMode()) out.put("testMode", true);
    return ResponseEntity.ok(out);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {
    String phone = normalize(Objects.toString(body.get("phone"), ""));
    if (phone == null) return bad("Digite um celular válido com DDD.");
    String err = codes.check("SMS", phone, Objects.toString(body.get("code"), ""));
    if (err != null) return bad(err);
    return ResponseEntity.ok(Map.of("phoneToken", codes.proof("phone", phone)));
  }

  /** Confere o comprovante do SMS: devolve o telefone confirmado, ou null. */
  public String phoneFromToken(String token) {
    return codes.fromProof("phone", token);
  }

  static ResponseEntity<?> bad(String m) { return ResponseEntity.badRequest().body(Map.of("message", m)); }
}
