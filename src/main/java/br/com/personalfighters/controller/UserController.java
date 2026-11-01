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
  private final br.com.personalfighters.service.AreaService areaService;
  private final UserRecordsService userRecordsService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna a lista paginada dos usuários.")
  public Object findAll(User user, Pageable pageRequest) {
    return userService.findAll(user, pageRequest);
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo ID.")
  public Object findById(@PathVariable Long id) {
    return userService.findById(id);
  }

  @GetMapping("/professionals/{ref}")
  @ResponseStatus(HttpStatus.OK)
  public Object findByProfessionalReference(@PathVariable String ref) {
    return userService.findByReference(ref);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public Object save(@RequestBody User user) {
    return userService.save(user);
  }

  @PutMapping
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Atualiza um usuário.")
  public User update(@RequestBody User user) {
    return userService.update(user);
  }

  @PostMapping("/records")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Salva um usuário novo.")
  public void saveRecords(@RequestBody UserRecords userRecords) {
    userRecordsService.save(userRecords);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Inativa o usuário pelo ID")
  public void setActive(@PathVariable Long id) {
    userService.delete(id);
  }

  @GetMapping("/email/{email}")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Retorna o usuário pelo e-mail.")
  @JsonView(Views.FirstLevel.class)
  public Object findByEmail(@PathVariable String email) {
    return userService.findByEmail(email);
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/admin")
  @ResponseStatus(HttpStatus.OK)
  @ApiOperation(value = "Dá o acesso de ADMIN para um usuário.")
  public Object setUserToAdmin(@RequestBody User user) {
    return userService.update(user);
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
    return out;
  }

}
