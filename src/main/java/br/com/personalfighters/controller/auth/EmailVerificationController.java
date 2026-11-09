package br.com.personalfighters.controller.auth;

import br.com.personalfighters.service.CodeStore;
import br.com.personalfighters.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Confirma o e-mail no cadastro com um código de 6 números (assim o "Esqueci a senha" sempre chega). */
@RestController
@RequestMapping("/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

  private final EmailService email;
  private final CodeStore codes;

  public static String normalize(String raw) {
    String e = raw == null ? "" : raw.trim().toLowerCase();
    return e.length() <= 120 && e.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$") ? e : null;
  }

  @PostMapping("/send")
  public ResponseEntity<?> send(@RequestBody Map<String, Object> body) {
    String e = normalize(Objects.toString(body.get("email"), ""));
    if (e == null) return PhoneVerificationController.bad("Digite um e-mail válido.");
    if (br.com.personalfighters.util.DisposableEmail.is(e)) return PhoneVerificationController.bad("Use um e-mail pessoal. E-mails temporários não são aceitos.");
    String code = codes.issue("EMAIL", e, !email.isConfigured());
    if (code == null) return PhoneVerificationController.bad("Espere um pouco para pedir outro código (máximo de 5 por dia).");
    email.sendText(e, "Seu código OPUS: " + code,
        "Olá!\n\nSeu código para confirmar este e-mail no OPUS é:\n\n       " + code + "\n\nEle vale por 10 minutos. Se não foi você, ignore este e-mail.\n\nEquipe OPUS");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("sent", true);
    if (!email.isConfigured()) out.put("testMode", true);
    return ResponseEntity.ok(out);
  }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@RequestBody Map<String, Object> body) {
    String e = normalize(Objects.toString(body.get("email"), ""));
    if (e == null) return PhoneVerificationController.bad("Digite um e-mail válido.");
    String err = codes.check("EMAIL", e, Objects.toString(body.get("code"), ""));
    if (err != null) return PhoneVerificationController.bad(err);
    return ResponseEntity.ok(Map.of("emailToken", codes.proof("email", e)));
  }
}
