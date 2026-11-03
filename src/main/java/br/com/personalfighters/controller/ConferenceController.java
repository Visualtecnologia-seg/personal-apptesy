package br.com.personalfighters.controller;

import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.Status;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.service.CompletionService;
import br.com.personalfighters.service.NotificationService;
import br.com.personalfighters.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static br.com.personalfighters.controller.SocialController.bad;
import static br.com.personalfighters.controller.SocialController.firstName;

/**
 * Conferência do serviço pelo cliente (Entrega 4).
 * O profissional marca "Terminei" → o cliente olha, testa, grava a prova e confirma →
 * o app do cliente mostra um código/QR → o PROFISSIONAL lê esse código e o pagamento é liberado.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ConferenceController {

  private static final int CODE_MINUTES = 5;
  private static final long MAX_PROOF_BYTES = 25L * 1024 * 1024;
  private static final SecureRandom RND = new SecureRandom();

  private final UserService userService;
  private final OrderRepository orderRepository;
  private final CompletionService completionService;
  private final NotificationService notificationService;
  private final JdbcTemplate jdbc;

  /** Estado da conferência (os dois lados usam). */
  @GetMapping("/orders/{id}/check")
  public ResponseEntity<?> state(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isCustomer(o, me) || isPro(o, me))) return bad("Pedido não encontrado.");
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT service_mode, check_items, client_checked_at, pro_checklist FROM order_ WHERE id = ?", id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("status", o.getStatus() == null ? null : o.getStatus().name());
    out.put("serviceMode", row.get("service_mode") == null ? "HOME" : row.get("service_mode"));
    out.put("checkItems", row.get("check_items"));
    out.put("proChecklist", row.get("pro_checklist"));
    out.put("clientChecked", row.get("client_checked_at") != null);
    out.put("professionalId", o.getProfessional() == null ? null : o.getProfessional().getId());
    out.put("requestedCompletionAt", o.getRequestedCompletionAt());
    out.put("autoReleaseAt", o.getRequestedCompletionAt() == null ? null : o.getRequestedCompletionAt().plusHours(CompletionService.AUTO_RELEASE_HOURS));
    out.put("released", Boolean.TRUE.equals(o.getPixReleased()));
    out.put("disputeOpen", completionService.disputeOpen(o));
    out.put("proofs", jdbc.queryForList(
        "SELECT id, content_type AS \"contentType\", created_at AS \"createdAt\" FROM order_photo_ WHERE order_id = ? AND kind = 'PROOF' ORDER BY created_at", id));
    out.put("requestPhotos", jdbc.queryForList(
        "SELECT id, content_type AS \"contentType\" FROM order_photo_ WHERE order_id = ? AND kind = 'REQUEST' ORDER BY created_at", id));
    return ResponseEntity.ok(out);
  }

  /** Profissional pode acrescentar itens na lista que o cliente vai conferir. */
  @PostMapping("/orders/{id}/pro-checklist")
  public ResponseEntity<?> proChecklist(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    String items = joinItems(body.get("items"), 8);
    jdbc.update("UPDATE order_ SET pro_checklist = ? WHERE id = ?", items, id);
    return ResponseEntity.ok(Map.of("proChecklist", items == null ? "" : items));
  }

  /** Prova em vídeo ou foto (envio em multipart: campo "file"). */
  @PostMapping(value = "/orders/{id}/proof", consumes = "multipart/form-data")
  public ResponseEntity<?> proof(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "kind", required = false) String kindParam) {
    String kind = "REQUEST".equalsIgnoreCase(kindParam) ? "REQUEST" : "PROOF";
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (file == null || file.isEmpty()) return bad("Arquivo vazio.");
    if (file.getSize() > MAX_PROOF_BYTES) return bad("O vídeo ficou grande demais. Grave até 30 segundos.");
    String type = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
    if (!type.startsWith("video/") && !type.startsWith("image/")) return bad("Envie um vídeo ou uma foto.");
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM order_photo_ WHERE order_id = ? AND kind = ?", Integer.class, id, kind);
    if (count != null && count >= 6) return bad("Você já enviou provas suficientes para este pedido.");
    try {
      UUID pid = UUID.randomUUID();
      jdbc.update("INSERT INTO order_photo_ (id, order_id, kind, content_type, data, created_at, size_bytes) VALUES (?, ?, ?, ?, ?, ?, ?)",
          pid, id, kind, type, file.getBytes(), Timestamp.valueOf(LocalDateTime.now()), (int) file.getSize());
      return ResponseEntity.ok(Map.of("id", pid, "contentType", type));
    } catch (Exception e) {
      log.warn("Prova do pedido {} não salva: {}", id, e.getMessage());
      return bad("Não foi possível salvar a prova. Tente de novo.");
    }
  }

  /** Cliente terminou de conferir: salva o que marcou e recebe o código para mostrar ao profissional. */
  @PostMapping("/orders/{id}/client-check")
  public ResponseEntity<?> clientCheck(@PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("O profissional ainda não marcou o serviço como pronto.");
    if (completionService.disputeOpen(o)) return bad("Há um problema relatado neste pedido. A equipe OPUS vai resolver.");
    String items = body == null ? null : joinItems(body.get("items"), 20);
    jdbc.update("UPDATE order_ SET check_items = ?, client_checked_at = COALESCE(client_checked_at, ?) WHERE id = ?",
        items, Timestamp.valueOf(completionService.now()), id);
    return ResponseEntity.ok(codeFor(o, true));
  }

  /** Código atual do cliente (muda a cada 5 minutos). */
  @GetMapping("/orders/{id}/client-code")
  public ResponseEntity<?> clientCode(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    if (Boolean.TRUE.equals(o.getPixReleased()) || o.getStatus() == Status.DONE || o.getStatus() == Status.REVIEWED) {
      return ResponseEntity.ok(Map.of("released", true));
    }
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("Este pedido não está aguardando a sua conferência.");
    Boolean checked = jdbc.queryForObject("SELECT client_checked_at IS NOT NULL FROM order_ WHERE id = ?", Boolean.class, id);
    if (!Boolean.TRUE.equals(checked)) return bad("Confira o serviço antes de mostrar o código.");
    return ResponseEntity.ok(codeFor(o, false));
  }

  /** Profissional lê o código (QR ou digitado) que aparece na tela do cliente. */
  @PostMapping("/orders/{id}/confirm-code")
  public ResponseEntity<?> confirmCode(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isPro(o, me)) return bad("Pedido não encontrado.");
    if (Boolean.TRUE.equals(o.getPixReleased())) return ResponseEntity.ok(Map.of("status", "DONE", "released", true));
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("Marque o serviço como terminado antes.");
    if (completionService.disputeOpen(o)) return bad("O cliente relatou um problema. A equipe OPUS vai analisar.");
    Boolean checked = jdbc.queryForObject("SELECT client_checked_at IS NOT NULL FROM order_ WHERE id = ?", Boolean.class, id);
    if (!Boolean.TRUE.equals(checked)) return bad("O cliente ainda está conferindo o serviço.");
    String raw = Objects.toString(body.get("code"), "");
    // O QR leva "OPUS:<pedido>:<código>"; digitado vem só o código.
    if (raw.startsWith("OPUS:")) {
      String[] parts = raw.split(":");
      if (parts.length != 3 || !parts[1].equalsIgnoreCase(id.toString())) return bad("Esse QR é de outro pedido.");
      raw = parts[2];
    }
    String code = raw.replaceAll("[^0-9]", "");
    if (o.getFinishCode() == null || !o.getFinishCode().equals(code)) return bad("Código incorreto. Peça ao cliente para mostrar a tela de novo.");
    if (o.getFinishCodeExpiresAt() != null && o.getFinishCodeExpiresAt().isBefore(completionService.now())) {
      return bad("O código mudou. Leia o código novo na tela do cliente.");
    }
    jdbc.update("UPDATE order_ SET released_by = 'CLIENT_CODE' WHERE id = ?", id);
    completionService.finalizeAndRelease(o, "código do cliente lido pelo profissional");
    notificationService.sendToUser(o.getCustomer(), false,
        "Prontinho! " + firstName(o.getProfessional()) + " leu o seu código e o pagamento foi liberado. Que tal avaliar o serviço?");
    return ResponseEntity.ok(Map.of("status", "DONE", "released", Boolean.TRUE.equals(o.getPixReleased())));
  }

  /** Serviço online: o cliente confirma com um botão (não há ninguém para ler o código). */
  @PostMapping("/orders/{id}/approve-online")
  public ResponseEntity<?> approveOnline(@PathVariable UUID id) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !isCustomer(o, me)) return bad("Pedido não encontrado.");
    String mode = jdbc.queryForObject("SELECT COALESCE(service_mode, 'HOME') FROM order_ WHERE id = ?", String.class, id);
    if (!"ONLINE".equalsIgnoreCase(mode)) return bad("Neste serviço, mostre o seu código ao profissional.");
    if (o.getStatus() != Status.PENDING_CLIENT_CONFIRMATION) return bad("O profissional ainda não marcou o serviço como pronto.");
    if (completionService.disputeOpen(o)) return bad("Há um problema relatado neste pedido.");
    jdbc.update("UPDATE order_ SET client_checked_at = COALESCE(client_checked_at, ?), released_by = 'CLIENT_ONLINE' WHERE id = ?",
        Timestamp.valueOf(completionService.now()), id);
    completionService.finalizeAndRelease(o, "serviço online confirmado pelo cliente");
    return ResponseEntity.ok(Map.of("status", "DONE", "released", Boolean.TRUE.equals(o.getPixReleased())));
  }

  /** Profissional define se o serviço é na casa do cliente, no local dele ou online. */
  @PostMapping("/orders/{id}/service-mode")
  public ResponseEntity<?> serviceMode(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    User me = userService.findAuthUser();
    Order o = orderRepository.findById(id).orElse(null);
    if (o == null || !(isPro(o, me) || isCustomer(o, me))) return bad("Pedido não encontrado.");
    String mode = Objects.toString(body.get("mode"), "").toUpperCase(Locale.ROOT);
    if (!Arrays.asList("HOME", "PRO_PLACE", "ONLINE").contains(mode)) return bad("Escolha: na casa do cliente, no local do profissional ou online.");
    jdbc.update("UPDATE order_ SET service_mode = ? WHERE id = ?", mode, id);
    return ResponseEntity.ok(Map.of("serviceMode", mode));
  }

  // ───────────── utilitários ─────────────

  private Map<String, Object> codeFor(Order o, boolean forceIfMissing) {
    LocalDateTime now = completionService.now();
    Timestamp rts = jdbc.queryForObject("SELECT code_rotated_at FROM order_ WHERE id = ?", Timestamp.class, o.getId());
    LocalDateTime rotated = rts == null ? null : rts.toLocalDateTime();
    boolean stale = o.getFinishCode() == null || rotated == null || rotated.plusMinutes(CODE_MINUTES).isBefore(now);
    if (stale || forceIfMissing && o.getFinishCode() == null) {
      String code = String.format("%06d", RND.nextInt(1_000_000));
      o.setFinishCode(code);
      o.setFinishCodeExpiresAt(now.plusMinutes(CODE_MINUTES + 1));
      orderRepository.save(o);
      rotated = now;
      jdbc.update("UPDATE order_ SET code_rotated_at = ? WHERE id = ?", Timestamp.valueOf(now), o.getId());
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("code", o.getFinishCode());
    out.put("qr", "OPUS:" + o.getId() + ":" + o.getFinishCode());
    out.put("changesAt", rotated.plusMinutes(CODE_MINUTES));
    out.put("released", false);
    return out;
  }

  private static String joinItems(Object raw, int max) {
    if (!(raw instanceof List)) return null;
    List<String> out = new ArrayList<>();
    for (Object it : (List<?>) raw) {
      String s = Objects.toString(it, "").replace('\n', ' ').trim();
      if (!s.isEmpty()) out.add(s.length() > 120 ? s.substring(0, 120) : s);
      if (out.size() >= max) break;
    }
    return out.isEmpty() ? null : String.join("\n", out);
  }

  private static boolean isPro(Order o, User me) {
    return o.getProfessional() != null && o.getProfessional().getId().equals(me.getId());
  }

  private static boolean isCustomer(Order o, User me) {
    return o.getCustomer() != null && o.getCustomer().getId().equals(me.getId());
  }
}
