package br.com.personalfighters.controller;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;
import br.com.personalfighters.service.UserRecordsService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.utils.Views;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@Api(tags = {"Users"})
@RequestMapping("/users")
public class UserController {

  private final UserService userService;
  private final br.com.personalfighters.util.Redactor redactor;

  private User me() { return userService.findAuthUser(); }
  private static org.springframework.web.server.ResponseStatusException denied() {
    return new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso não permitido.");
  }
  private void selfOrAdmin(Long id) {
    if (!br.com.personalfighters.util.Redactor.isAdmin() && (id == null || !id.equals(me().getId()))) throw denied();
  }
  private final br.com.personalfighters.service.AreaService areaService;
  private final UserRecordsService userRecordsService;
  private final ProGrowthController proGrowthController;
  private final br.com.personalfighters.controller.auth.PhoneVerificationController phoneVerification;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final br.com.personalfighters.service.CodeStore codes;
  private final br.com.personalfighters.service.SecurityService securityService;
  private final br.com.personalfighters.service.PayoutService payoutService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada dos usuários.")
  public Object findAll(User user, Pageable pageRequest) {
    return redactor.strip(userService.findAll(user, pageRequest), me().getId());
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo ID.")
  public Object findById(@PathVariable Long id) {
    return redactor.strip(userService.findById(id), me().getId());
  }

  @GetMapping("/professionals/{ref}")
  @ResponseStatus(HttpStatus.OK)
  public Object findByProfessionalReference(@PathVariable String ref) {
    return redactor.strip(userService.findByReference(ref), me().getId());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public Object save(@RequestBody User in,
                     @RequestHeader(value = "X-Phone-Token", required = false) String phoneToken,
                     @RequestHeader(value = "X-Email-Token", required = false) String emailToken,
                     @RequestHeader(value = "X-Google-Token", required = false) String googleToken,
                     @RequestHeader(value = "X-Apple-Token", required = false) String appleToken,
                     @RequestHeader(value = "X-Referral-Code", required = false) String referralCode,
                     @RequestHeader(value = "X-Terms-Version", required = false) String termsVersion) {
    boolean admin = br.com.personalfighters.util.Redactor.isAdmin();
    String phone = br.com.personalfighters.controller.auth.PhoneVerificationController.normalize(in.getPhoneNumber());
    String email = br.com.personalfighters.controller.auth.EmailVerificationController.normalize(in.getEmail());
    String googleSub = null, appleSub = null;
    if (!admin) {
      // 1) termos de uso e privacidade aceitos
      if (termsVersion == null || termsVersion.isBlank()) throw badReq("Aceite os Termos de Uso e a Política de Privacidade para continuar.");
      // 2) maior de 18 anos
      java.time.LocalDate b = in.getBirthday();
      if (b == null) throw badReq("Informe sua data de nascimento.");
      int age = java.time.Period.between(b, java.time.LocalDate.now()).getYears();
      if (age < 18) throw badReq("O OPUS é só para maiores de 18 anos.");
      if (age > 110) throw badReq("Confira a data de nascimento.");
      // 3) celular confirmado por SMS e ainda sem outra conta
      if (phone == null) throw badReq("Digite um celular válido com DDD.");
      if (!phone.equals(phoneVerification.phoneFromToken(phoneToken))) throw badReq("Confirme o seu celular com o código enviado por SMS.");
      Integer samePhone = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE phone_verified = TRUE AND regexp_replace(COALESCE(phone_number, ''), '[^0-9]', '', 'g') = ?", Integer.class, phone);
      if (samePhone != null && samePhone > 0) throw badReq("Este celular já tem uma conta. Entre com ele ou use \"Esqueci minha senha\".");
      // 4) e-mail confirmado (por código ou pela conta Google)
      if (email == null) throw badReq("Digite um e-mail válido.");
      if (br.com.personalfighters.util.DisposableEmail.is(email)) throw badReq("Use um e-mail pessoal. E-mails temporários não são aceitos.");
      String g = codes.fromProof("google", googleToken);
      String ap = codes.fromProof("apple", appleToken);
      if (g != null && g.startsWith(email + "|")) googleSub = g.substring(email.length() + 1);
      else if (ap != null && ap.startsWith(email + "|")) appleSub = ap.substring(email.length() + 1);
      else if (!email.equals(codes.fromProof("email", emailToken))) throw badReq("Confirme o seu e-mail com o código que enviamos.");
    }
    // Cadastro novo: copia SÓ os campos que a pessoa pode escolher.
    // (Antes, mandar o "id" de outra pessoa sobrescrevia a conta dela.)
    User user = new User();
    String uname = in.getUsername() == null ? "" : in.getUsername().trim();
    user.setUsername(uname.isEmpty() ? newUsername(in.getName()) : uname);
    user.setPassword(in.getPassword());
    user.setName(in.getName());
    user.setSurname(in.getSurname());
    user.setBirthday(in.getBirthday());
    user.setCpf(in.getCpf());
    user.setGender(in.getGender());
    user.setPhoneNumber(phone != null ? phone : in.getPhoneNumber());
    user.setEmail(email != null ? email : in.getEmail());
    user.setLatitude(in.getLatitude());
    user.setLongitude(in.getLongitude());
    user.setFullAddress(in.getFullAddress());
    // só indica de qual app veio (o cadastro sempre recebe os dois papéis)
    if (in.getRole() != null && in.getRole().size() == 1 && in.getRole().get(0) == br.com.personalfighters.model.Role.PROFESSIONAL)
      user.setRole(new java.util.ArrayList<>(java.util.List.of(br.com.personalfighters.model.Role.PROFESSIONAL)));
    User saved = userService.save(user);
    if (!admin) {
      jdbc.update("UPDATE user_ SET phone_verified = TRUE, email_verified = TRUE, terms_version = ?, terms_accepted_at = NOW(), google_sub = ? WHERE id = ?",
          termsVersion.length() > 20 ? termsVersion.substring(0, 20) : termsVersion, googleSub, saved.getId());
      if (appleSub != null) jdbc.update("UPDATE user_ SET apple_sub = ? WHERE id = ?", appleSub, saved.getId());
      // Código de quem indicou (opcional): os dois ganham pontos no primeiro serviço
      if (referralCode != null && !referralCode.isBlank()) {
        java.util.List<Long> ref = jdbc.queryForList("SELECT id FROM user_ WHERE referral_code = ?", Long.class, referralCode.trim().toUpperCase());
        if (!ref.isEmpty() && !ref.get(0).equals(saved.getId()))
          jdbc.update("INSERT INTO referral_ (referred_id, referrer_id, created_at) VALUES (?, ?, NOW())", saved.getId(), ref.get(0));
      }
    }
    return redactor.strip(saved, saved.getId()); // nunca devolve a senha (nem criptografada)
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Atualiza um usuário.")
  public Object update(@RequestBody User user, @RequestHeader(value = "X-Phone-Token", required = false) String phoneToken) {
    String[] phoneChange = null;
    if (!br.com.personalfighters.util.Redactor.isAdmin()) {
      // Cada um só altera a própria conta, e nunca o próprio cargo, verificação ou documentos.
      User cur = me();
      user.setId(cur.getId());
      user.setRole(cur.getRole());
      user.setCpf(cur.getCpf()); // CPF é fixo depois do cadastro
      user.setVerified(cur.getVerified());
      user.setVerificationStatus(cur.getVerificationStatus());
      user.setVerificationReason(cur.getVerificationReason());
      user.setActive(cur.getActive());
      user.setAsaasCustomerId(cur.getAsaasCustomerId());
      user.setDocumentUrl(cur.getDocumentUrl());
      user.setDocFrontUrl(cur.getDocFrontUrl());
      user.setDocBackUrl(cur.getDocBackUrl());
      user.setDocSelfieUrl(cur.getDocSelfieUrl());
      // Sem esses blocos no envio, mantém os atuais (antes um envio incompleto apagava o perfil profissional)
      if (user.getProfessional() == null || cur.getProfessional() == null) user.setProfessional(cur.getProfessional());
      else {
        // Nota, nº de avaliações, destaque (pago) e selo de resposta rápida são do sistema: o profissional não muda
        br.com.personalfighters.entity.Professional np = user.getProfessional(), cp = cur.getProfessional();
        np.setId(cp.getId());
        np.setRating(cp.getRating());
        np.setCount(cp.getCount());
        np.setFeatured(cp.getFeatured());
        np.setFastResponder(cp.getFastResponder());
        np.setActive(cp.getActive());
        np.setReference(cp.getReference());
      }
      if (user.getCustomer() == null || cur.getCustomer() == null) user.setCustomer(cur.getCustomer());
      else { user.getCustomer().setId(cur.getCustomer().getId()); user.getCustomer().setActive(cur.getCustomer().getActive()); }
      // Foto só pode apontar para o próprio servidor (enviada pela tela de foto); nada de link externo
      String av = user.getAvatarUrl();
      if (av == null || !(av.isEmpty() || av.startsWith("/users/" + cur.getId() + "/avatar"))) user.setAvatarUrl(cur.getAvatarUrl());
      if (user.getPixKey() == null) { user.setPixKey(cur.getPixKey()); user.setPixKeyType(cur.getPixKeyType()); }
      else if (!user.getPixKey().equals(cur.getPixKey())) {
        // Chave PIX só pode ser o CPF da própria conta ou o CNPJ (MEI) dela já conferido: o banco confirma o titular
        String keyDigits = user.getPixKey().replaceAll("\\D", "");
        String docType = payoutService.ownDocType(cur, keyDigits);
        if (docType == null)
          throw badReq("A chave PIX precisa ser o seu CPF ou o CNPJ (MEI) confirmado da sua conta.");
        user.setPixKey(keyDigits);
        user.setPixKeyType(docType);
      }
      if (user.getLatitude() == null) { user.setLatitude(cur.getLatitude()); user.setLongitude(cur.getLongitude()); }
      if (user.getPhoneNumber() == null) user.setPhoneNumber(cur.getPhoneNumber());
      // Trocar o celular exige o código por SMS no número NOVO; o número antigo é avisado e o saque fica segurado 24 h
      String oldPhone = br.com.personalfighters.controller.auth.PhoneVerificationController.normalize(cur.getPhoneNumber());
      String newPhone = br.com.personalfighters.controller.auth.PhoneVerificationController.normalize(user.getPhoneNumber());
      String rawNew = user.getPhoneNumber() == null ? "" : user.getPhoneNumber().replaceAll("\\D", "");
      String rawOld = cur.getPhoneNumber() == null ? "" : cur.getPhoneNumber().replaceAll("\\D", "");
      if (!rawNew.equals(rawOld)) {
        if (newPhone == null) throw badReq("Digite um celular válido com DDD.");
        if (!newPhone.equals(oldPhone)) {
          if (!newPhone.equals(phoneVerification.phoneFromToken(phoneToken))) throw badReq("Confirme o novo celular com o código enviado por SMS.");
          Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE id <> ? AND phone_verified = TRUE AND regexp_replace(COALESCE(phone_number, ''), '[^0-9]', '', 'g') = ?", Integer.class, cur.getId(), newPhone);
          if (taken != null && taken > 0) throw badReq("Este celular já está em outra conta.");
          phoneChange = new String[]{oldPhone, newPhone};
        }
        user.setPhoneNumber(newPhone);
      }
      if (user.getFullAddress() == null) user.setFullAddress(cur.getFullAddress());
      // campos que a tela de editar perfil não manda continuam como estão
      if (user.getBirthday() == null) user.setBirthday(cur.getBirthday());
      if (user.getGender() == null) user.setGender(cur.getGender());
    }
    String oldEmail = br.com.personalfighters.util.Redactor.isAdmin() ? null : me().getEmail();
    if (oldEmail != null && user.getEmail() != null && !oldEmail.equalsIgnoreCase(user.getEmail()) && br.com.personalfighters.util.DisposableEmail.is(user.getEmail()))
      throw badReq("Use um e-mail pessoal. E-mails temporários não são aceitos.");
    User saved = userService.update(user);
    // trocou o e-mail: precisa confirmar de novo
    if (oldEmail != null && saved.getEmail() != null && !oldEmail.equalsIgnoreCase(saved.getEmail()))
      jdbc.update("UPDATE user_ SET email_verified = FALSE WHERE id = ?", saved.getId());
    if (phoneChange != null) {
      jdbc.update("UPDATE user_ SET phone_verified = TRUE WHERE id = ?", saved.getId());
      securityService.phoneChanged(saved, phoneChange[0], phoneChange[1]);
    }
    return redactor.strip(saved, saved.getId());
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public void saveRecords(@RequestBody UserRecords userRecords) {
    // Sempre da própria conta (antes dava para cadastrar o celular de outra pessoa e receber os avisos dela)
    userRecords.setUser(me());
    userRecords.setId(null);
    userRecordsService.save(userRecords);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Inativa o usuário pelo ID")
  public void setActive(@PathVariable Long id) {
    selfOrAdmin(id);
    userService.delete(id);
  }

  @GetMapping("/email/{email}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo e-mail.")
  @JsonView(Views.FirstLevel.class)
  public Object findByEmail(@PathVariable String email) {
    if (!br.com.personalfighters.util.Redactor.isAdmin() && !email.equalsIgnoreCase(String.valueOf(me().getEmail()))) throw denied();
    return userService.findByEmail(email);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/admin")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Dá o acesso de ADMIN para um usuário.")
  public Object setUserToAdmin(@RequestBody User user) {
    User saved = userService.update(user);
    return redactor.strip(saved, saved.getId());
  }

  private static org.springframework.web.server.ResponseStatusException badReq(String m) {
    return new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }

  /** "Maria da Silva" → "maria.silva4821" (a pessoa entra com CPF, e-mail ou telefone; o usuário é só um apelido interno). */
  private String newUsername(String name) {
    String base = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z ]", " ").trim();
    String[] p = base.isEmpty() ? new String[]{"opus"} : base.split("\\s+");
    String b = p.length > 1 ? p[0] + "." + p[p.length - 1] : p[0];
    if (b.length() > 24) b = b.substring(0, 24);
    java.security.SecureRandom r = new java.security.SecureRandom();
    for (int i = 0; i < 20; i++) {
      String u = b + (1000 + r.nextInt(9000));
      Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM user_ WHERE username = ?", Integer.class, u);
      if (n != null && n == 0) return u;
    }
    return b + System.currentTimeMillis();
  }

  @PutMapping("/{id}/request-verification")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Prestador envia dados e documentos para solicitar verificação.")
  public Object requestVerification(@PathVariable Long id,
                                    @RequestBody java.util.Map<String, String> payload) {
    User me = userService.findAuthUser();
    if (me == null || !me.getId().equals(id)) {
      throw new org.springframework.security.access.AccessDeniedException("Você só pode enviar a sua própria verificação.");
    }
    return userService.requestVerification(id, payload);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/{id}/verify")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Marca o utilizador como verificado (documentos aprovados).")
  public Object verifyUser(@PathVariable Long id,
                           @RequestParam(required = false) String documentUrl) {
    return userService.verifyUser(id, documentUrl);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/{id}/reject-verification")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Rejeita a verificação do utilizador.")
  public Object rejectVerification(@PathVariable Long id,
                                   @RequestBody(required = false) java.util.Map<String, String> body) {
    String reason = body != null ? body.getOrDefault("reason", "") : "";
    return userService.rejectVerification(id, reason);
  }

  @GetMapping("/{id}/verification-status")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o estado de verificação do utilizador.")
  public Object getVerificationStatus(@PathVariable Long id) {
    selfOrAdmin(id);
    User user = userService.findById(id);
    return java.util.Map.of(
        "id", user.getId(),
        "name", user.getName() != null ? user.getName() : "",
        "verified", Boolean.TRUE.equals(user.getVerified()),
        "verificationStatus", user.getVerificationStatus() != null ? user.getVerificationStatus() : "NONE",
        "reason", user.getVerificationReason() != null ? user.getVerificationReason() : ""
    );
  }

  @PutMapping("/{id}/location")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Atualiza a localização GPS do utilizador.")
  public Object updateLocation(@PathVariable Long id,
                               @RequestParam double lat,
                               @RequestParam double lng) {
    selfOrAdmin(id);
    return userService.updateLocation(id, lat, lng);
  }

  @GetMapping("/professionals/nearby")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna profissionais próximos num raio especificado.")
  public Object nearbyProfessionals(@RequestParam double lat,
                                    @RequestParam double lng,
                                    @RequestParam(defaultValue = "20") double radiusKm,
                                    @RequestParam(required = false) String neighborhood) {
    java.util.List<User> base = userService.nearbyProfessionals(lat, lng, Math.max(radiusKm, 60));
    java.util.List<User> out = new java.util.ArrayList<>();
    java.util.Set<Long> seen = new java.util.HashSet<>();
    for (User u : base) {
      Boolean ok = areaService.covers(u, lat, lng, neighborhood);
      boolean inRadius = u.getLatitude() != null && u.getLongitude() != null
          && br.com.personalfighters.service.AreaService.km(lat, lng, u.getLatitude(), u.getLongitude()) <= radiusKm;
      if (Boolean.TRUE.equals(ok) || (ok == null && inRadius)) { out.add(u); seen.add(u.getId()); }
    }
    // profissionais em viagem para esta região aparecem também
    for (Long id : areaService.travellersNear(lat, lng)) {
      if (seen.contains(id)) continue;
      try { User u = userService.findById(id); if (u != null && u.getProfessional() != null && Boolean.TRUE.equals(u.getActive())) out.add(u); } catch (Exception ignored) { }
    }
    // destaque pago aparece primeiro
    try {
      java.util.Set<Long> boosted = new java.util.HashSet<>(proGrowthController.boostedIds());
      out.sort((a, b) -> Boolean.compare(boosted.contains(b.getId()), boosted.contains(a.getId())));
    } catch (Exception ignored) { }
    return out;
  }

}
