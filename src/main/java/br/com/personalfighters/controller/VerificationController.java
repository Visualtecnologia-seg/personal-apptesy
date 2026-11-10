package br.com.personalfighters.controller;

import br.com.personalfighters.entity.IdentityVerification;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.IdentityVerificationRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Verificação de identidade com análise manual:
 *  - o app envia documento (frente/verso), selfie neutra, selfie fazendo um gesto sorteado
 *    e selfie segurando o documento, junto com o aceite do termo (LGPD);
 *  - o administrador compara tudo lado a lado e aprova ou recusa, indicando o motivo
 *    e quais etapas o usuário precisa refazer.
 */
@Slf4j
@RestController
@RequestMapping("/verifications")
@RequiredArgsConstructor
public class VerificationController {

  private static final int MAX_IMAGE_CHARS = 4_000_000; // ~3 MB por imagem
  private static final Set<String> STEPS = Set.of("DOC_FRONT", "DOC_BACK", "FACE", "SELFIE_DOC");

  private final IdentityVerificationRepository repo;
  private final UserRepository userRepository;
  private final UserService userService;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  private final br.com.personalfighters.service.SecurityService securityService;
  private final br.com.personalfighters.service.EmailAutomationService emailAutomation;

  // ───────────────────────────── App (o próprio usuário) ─────────────────────────────

  @GetMapping("/me")
  public Map<String, Object> myStatus() {
    User me = userService.findAuthUser();
    Optional<IdentityVerification> last = repo.findFirstByUserIdOrderBySubmittedAtDesc(me.getId());
    Map<String, Object> out = new LinkedHashMap<>();
    String status = Boolean.TRUE.equals(me.getVerified()) ? "APPROVED"
        : last.map(IdentityVerification::getStatus).orElse("NONE");
    out.put("status", status);
    out.put("verified", Boolean.TRUE.equals(me.getVerified()));
    last.ifPresent(v -> {
      out.put("submittedAt", v.getSubmittedAt());
      out.put("reviewedAt", v.getReviewedAt());
      out.put("reason", v.getRejectionReason() == null ? "" : v.getRejectionReason());
      out.put("rejectedSteps", splitSteps(v.getRejectedSteps()));
    });
    return out;
  }

  /** Checagem automática da foto antes de enviar (nitidez, luz, reflexo, tamanho). */
  @PostMapping("/check-image")
  public Map<String, Object> checkImage(@RequestBody Map<String, String> body) {
    userService.findAuthUser();
    return br.com.personalfighters.util.ImageQuality.check(body.get("image"), body.getOrDefault("kind", "DOC"));
  }

  @PostMapping("/me")
  public ResponseEntity<?> submit(@RequestBody Map<String, String> body) {
    User me = userService.findAuthUser();
    if (Boolean.TRUE.equals(me.getVerified())) {
      return bad("Sua conta já está verificada.");
    }
    if (!"true".equalsIgnoreCase(body.getOrDefault("consent", ""))) {
      return bad("É preciso aceitar o termo de uso dos dados para continuar.");
    }
    String docType = body.getOrDefault("docType", "RG").toUpperCase();
    if (!docType.equals("RG") && !docType.equals("CNH")) docType = "RG";

    // Reaproveita imagens da tentativa anterior que não precisam ser refeitas
    Optional<IdentityVerification> prev = repo.findFirstByUserIdOrderBySubmittedAtDesc(me.getId());
    if (prev.isPresent() && "PENDING".equals(prev.get().getStatus())) {
      return bad("Você já tem uma verificação em análise.");
    }
    IdentityVerification p = prev.orElse(null);

    String front = pick(body.get("docFront"), p == null ? null : p.getDocFront());
    String back = pick(body.get("docBack"), p == null ? null : p.getDocBack());
    String selfie = pick(body.get("selfieNeutral"), p == null ? null : p.getSelfieNeutral());
    // A prova de vida (rosto) é sempre refeita junto com a selfie
    String turnA = body.get("selfieTurnA");
    String turnB = body.get("selfieTurnB");
    boolean liveness = "true".equalsIgnoreCase(body.getOrDefault("livenessPassed", ""));
    String livenessInfo = body.getOrDefault("livenessInfo", "");
    if (livenessInfo.length() > 250) livenessInfo = livenessInfo.substring(0, 250);
    if (selfie != null && selfie == (p == null ? null : p.getSelfieNeutral()) && (turnA == null || turnA.isBlank())) {
      // selfie antiga reaproveitada: mantém também as fotos da prova de vida
      turnA = p.getSelfieTurnA();
      turnB = p.getSelfieTurnB();
      liveness = Boolean.TRUE.equals(p.getLivenessPassed());
      livenessInfo = p.getLivenessInfo();
    }
    String selfieDoc = pick(body.get("selfieWithDoc"), p == null ? null : p.getSelfieWithDoc());

    for (String[] img : new String[][]{
        {front, "a frente do documento"}, {back, "o verso do documento"},
        {selfie, "a selfie"}, {turnA, "a foto do rosto virado para um lado"},
        {turnB, "a foto do rosto virado para o outro lado"}, {selfieDoc, "a selfie com o documento"}}) {
      if (img[0] == null || img[0].isBlank()) return bad("Falta enviar " + img[1] + ".");
      if (!img[0].startsWith("data:image/")) return bad("Formato de imagem inválido em " + img[1] + ".");
      if (img[0].length() > MAX_IMAGE_CHARS) return bad("Imagem muito grande em " + img[1] + ".");
    }

    IdentityVerification v = IdentityVerification.builder()
        .userId(me.getId())
        .status("PENDING")
        .docType(docType)
        .docFront(front)
        .docBack(back)
        .selfieNeutral(selfie)
        .selfieTurnA(turnA)
        .selfieTurnB(turnB)
        .livenessPassed(liveness)
        .livenessInfo(livenessInfo)
        .selfieWithDoc(selfieDoc)
        .consentVersion(body.getOrDefault("consentVersion", "1"))
        .consentAt(LocalDateTime.now())
        .submittedAt(LocalDateTime.now())
        .build();
    repo.save(v);
    try {
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("docFront", br.com.personalfighters.util.ImageQuality.check(front, "DOC"));
      report.put("docBack", br.com.personalfighters.util.ImageQuality.check(back, "DOC"));
      report.put("selfie", br.com.personalfighters.util.ImageQuality.check(selfie, "SELFIE"));
      report.put("selfieWithDoc", br.com.personalfighters.util.ImageQuality.check(selfieDoc, "SELFIE"));
      jdbc.update("UPDATE identity_verification_ SET quality_report = ? WHERE id = ?",
          new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report), v.getId());
    } catch (Exception e) {
      log.warn("Checagem de qualidade não salva: {}", e.getMessage());
    }

    // Dados pessoais conferidos pelo usuário na etapa "Seus dados"
    String fullName = body.getOrDefault("fullName", "").trim();
    if (!fullName.isEmpty()) me.setName(fullName);
    String cpf = body.getOrDefault("cpf", "").replaceAll("[^0-9]", "");
    if (cpf.length() == 11) me.setCpf(cpf);
    String birthday = body.getOrDefault("birthday", "").trim();
    if (!birthday.isEmpty()) {
      try { me.setBirthday(java.time.LocalDate.parse(birthday)); } catch (Exception ignored) {}
    }
    String phone = body.getOrDefault("phoneNumber", "").trim();
    if (!phone.isEmpty()) me.setPhoneNumber(phone);
    String address = body.getOrDefault("fullAddress", "").trim();
    if (!address.isEmpty()) me.setFullAddress(address);

    me.setVerificationStatus("PENDING");
    me.setVerificationReason(null);
    userRepository.save(me);
    log.info("Verificação enviada pelo usuário {}", me.getId());
    return ResponseEntity.ok(Map.of("status", "PENDING"));
  }

  // ───────────────────────────── Administração ─────────────────────────────

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(defaultValue = "PENDING") String status) {
    List<IdentityVerification> items = "ALL".equalsIgnoreCase(status)
        ? repo.findTop100ByOrderBySubmittedAtDesc()
        : repo.findByStatusOrderBySubmittedAtAsc(status.toUpperCase());
    return items.stream().map(v -> {
      Map<String, Object> m = summary(v);
      return m;
    }).collect(Collectors.toList());
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @GetMapping("/{id}")
  public ResponseEntity<?> detail(@PathVariable UUID id) {
    User viewer = userService.findAuthUser();
    if (!securityService.canViewDocs(viewer.getId()))
      return ResponseEntity.status(403).body(Map.of("message", "Você não tem permissão para ver documentos e selfies. Peça para quem cuida do painel liberar em Equipe."));
    return repo.findById(id).map(v -> {
      securityService.logDocAccess(viewer, v.getUserId(), "Documentos e selfies da verificação");
      Map<String, Object> m = summary(v);
      m.put("docFront", v.getDocFront());
      m.put("docBack", v.getDocBack());
      m.put("selfieNeutral", v.getSelfieNeutral());
      m.put("selfieTurnA", v.getSelfieTurnA());
      m.put("selfieTurnB", v.getSelfieTurnB());
      m.put("selfieWithDoc", v.getSelfieWithDoc());
      try {
        String q = jdbc.queryForObject("SELECT quality_report FROM identity_verification_ WHERE id = ?", String.class, id);
        if (q != null) m.put("quality", new com.fasterxml.jackson.databind.ObjectMapper().readValue(q, Map.class));
      } catch (Exception ignored) { }
      return ResponseEntity.ok(m);
    }).orElse(ResponseEntity.notFound().build());
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/{id}/approve")
  public ResponseEntity<?> approve(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    // O administrador precisa confirmar que nome, CPF, nascimento e rosto batem com o documento
    Object ck = body == null ? null : body.get("checks");
    Map<?, ?> checks = ck instanceof Map ? (Map<?, ?>) ck : Map.of();
    for (String k : new String[]{"name", "cpf", "birth", "face"}) {
      if (!Boolean.TRUE.equals(checks.get(k))) return bad("Confira nome, CPF, nascimento e rosto com o documento e marque os quatro antes de aprovar.");
    }
    return repo.findById(id).map(v -> {
      User admin = userService.findAuthUser();
      try {
        jdbc.update("INSERT INTO admin_action_ (admin_id, admin_name, action, target, detail, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
            admin.getId(), java.util.Objects.toString(admin.getName(), "Painel"), "Aprovou verificação", String.valueOf(v.getUserId()), "Conferiu nome, CPF, nascimento e rosto com o documento");
      } catch (Exception ignored) { }
      v.setStatus("APPROVED");
      v.setReviewedAt(LocalDateTime.now());
      v.setReviewedBy(admin.getUsername());
      v.setRejectionReason(null);
      v.setRejectedSteps(null);
      repo.save(v);
      userRepository.findById(v.getUserId()).ifPresent(u -> {
        u.setVerified(true);
        u.setVerificationStatus("APPROVED");
        u.setVerificationReason(null);
        userRepository.save(u);
      });
      emailAutomation.verificationApproved(v.getUserId());
      return ResponseEntity.ok(summary(v));
    }).orElse(ResponseEntity.notFound().build());
  }

  @PreAuthorize("hasRole('ROLE_ADMIN')")
  @PutMapping("/{id}/reject")
  public ResponseEntity<?> reject(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    String reason = Objects.toString(body.get("reason"), "").trim();
    if (reason.isEmpty()) return bad("Informe o motivo da recusa.");
    List<String> steps = new ArrayList<>();
    Object raw = body.get("steps");
    if (raw instanceof Collection) {
      for (Object s : (Collection<?>) raw) {
        String st = Objects.toString(s, "").toUpperCase();
        if (STEPS.contains(st)) steps.add(st);
      }
    }
    if (steps.isEmpty()) steps.addAll(STEPS);
    return repo.findById(id).map(v -> {
      User admin = userService.findAuthUser();
      v.setStatus("REJECTED");
      v.setReviewedAt(LocalDateTime.now());
      v.setReviewedBy(admin.getUsername());
      v.setRejectionReason(reason);
      v.setRejectedSteps(String.join(",", steps));
      // Etapas recusadas são apagadas para o usuário enviar de novo
      if (steps.contains("DOC_FRONT")) v.setDocFront(null);
      if (steps.contains("DOC_BACK")) v.setDocBack(null);
      if (steps.contains("FACE")) {
        v.setSelfieNeutral(null);
        v.setSelfieTurnA(null);
        v.setSelfieTurnB(null);
      }
      if (steps.contains("SELFIE_DOC")) v.setSelfieWithDoc(null);
      repo.save(v);
      userRepository.findById(v.getUserId()).ifPresent(u -> {
        u.setVerified(false);
        u.setVerificationStatus("REJECTED");
        u.setVerificationReason(reason);
        userRepository.save(u);
      });
      emailAutomation.verificationRejected(v.getUserId(), reason, steps);
      return ResponseEntity.ok(summary(v));
    }).orElse(ResponseEntity.notFound().build());
  }

  // ───────────────────────────── utilitários ─────────────────────────────

  private Map<String, Object> summary(IdentityVerification v) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", v.getId());
    m.put("userId", v.getUserId());
    m.put("status", v.getStatus());
    m.put("docType", v.getDocType());
    m.put("livenessPassed", Boolean.TRUE.equals(v.getLivenessPassed()));
    m.put("livenessInfo", v.getLivenessInfo());
    m.put("submittedAt", v.getSubmittedAt());
    m.put("reviewedAt", v.getReviewedAt());
    m.put("reviewedBy", v.getReviewedBy());
    m.put("reason", v.getRejectionReason());
    m.put("rejectedSteps", splitSteps(v.getRejectedSteps()));
    m.put("consentAt", v.getConsentAt());
    userRepository.findById(v.getUserId()).ifPresent(u -> {
      m.put("name", u.getName());
      m.put("email", u.getEmail());
      m.put("cpf", u.getCpf());
      m.put("birthday", u.getBirthday());
      m.put("isProfessional", u.getRole() != null && u.getRole().toString().contains("PROFESSIONAL"));
    });
    return m;
  }

  private static List<String> splitSteps(String s) {
    if (s == null || s.isBlank()) return Collections.emptyList();
    return Arrays.asList(s.split(","));
  }

  private static String pick(String fresh, String previous) {
    return (fresh != null && !fresh.isBlank()) ? fresh : previous;
  }

  private static ResponseEntity<Map<String, String>> bad(String msg) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", msg));
  }
}
