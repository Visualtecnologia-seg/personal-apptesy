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
  public Object save(@RequestBody User in, @RequestHeader(value = "X-Phone-Token", required = false) String phoneToken) {
    // Telefone confirmado por SMS é obrigatório no cadastro (o administrador pode cadastrar sem)
    String phone = br.com.personalfighters.controller.auth.PhoneVerificationController.normalize(in.getPhoneNumber());
    boolean admin = br.com.personalfighters.util.Redactor.isAdmin();
    if (!admin) {
      if (phone == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Digite um celular válido com DDD.");
      if (!phone.equals(phoneVerification.phoneFromToken(phoneToken)))
        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirme o seu celular com o código enviado por SMS.");
    }
    // Cadastro novo: copia SÓ os campos que a pessoa pode escolher.
    // (Antes, mandar o "id" de outra pessoa sobrescrevia a conta dela.)
    User user = new User();
    user.setUsername(in.getUsername() == null ? null : in.getUsername().trim());
    user.setPassword(in.getPassword());
    user.setName(in.getName());
    user.setSurname(in.getSurname());
    user.setBirthday(in.getBirthday());
    user.setCpf(in.getCpf());
    user.setGender(in.getGender());
    user.setPhoneNumber(phone != null ? phone : in.getPhoneNumber());
    user.setEmail(in.getEmail() == null ? null : in.getEmail().trim());
    user.setLatitude(in.getLatitude());
    user.setLongitude(in.getLongitude());
    user.setFullAddress(in.getFullAddress());
    User saved = userService.save(user);
    if (!admin) jdbc.update("UPDATE user_ SET phone_verified = TRUE WHERE id = ?", saved.getId());
    return redactor.strip(saved, saved.getId()); // nunca devolve a senha (nem criptografada)
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Atualiza um usuário.")
  public Object update(@RequestBody User user) {
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
      if (user.getLatitude() == null) { user.setLatitude(cur.getLatitude()); user.setLongitude(cur.getLongitude()); }
      if (user.getPhoneNumber() == null) user.setPhoneNumber(cur.getPhoneNumber());
      if (user.getFullAddress() == null) user.setFullAddress(cur.getFullAddress());
    }
    User saved = userService.update(user);
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
