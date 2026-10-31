package br.com.personalfighters.controller;

import br.com.personalfighters.entity.BlockedAttempt;
import br.com.personalfighters.entity.Order;
import br.com.personalfighters.entity.ProtectionSession;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.BlockedAttemptRepository;
import br.com.personalfighters.repository.OrderRepository;
import br.com.personalfighters.repository.ProtectionSessionRepository;
import br.com.personalfighters.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/protection")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class ProtectionController {

  private final ProtectionSessionRepository protectionRepo;
  private final BlockedAttemptRepository blockedRepo;
  private final OrderRepository orderRepo;
  private final UserRepository userRepo;

  @PostMapping("/start")
  public ResponseEntity<ProtectionSession> start(@RequestBody Map<String, Object> body) {
    UUID orderId = UUID.fromString(body.get("orderId").toString());
    Long userId = Long.parseLong(body.get("userId").toString());
    int level = body.containsKey("level") ? Integer.parseInt(body.get("level").toString()) : 1;

    Optional<ProtectionSession> existing = protectionRepo.findByOrderIdAndUserIdAndActiveTrue(orderId, userId);
    if (existing.isPresent()) return ResponseEntity.ok(existing.get());

    ProtectionSession session = ProtectionSession.builder()
        .orderId(orderId)
        .userId(userId)
        .level(level)
        .active(true)
        .build();
    return ResponseEntity.ok(protectionRepo.save(session));
  }

  @PostMapping("/{sessionId}/escalate")
  public ResponseEntity<ProtectionSession> escalate(
      @PathVariable UUID sessionId,
      @RequestBody Map<String, Object> body) {
    return protectionRepo.findById(sessionId).map(session -> {
      int level = Integer.parseInt(body.getOrDefault("level", "2").toString());
      session.setLevel(level);
      session.setEscalatedAt(LocalDateTime.now());
      return ResponseEntity.ok(protectionRepo.save(session));
    }).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/{sessionId}/stop")
  public ResponseEntity<Void> stop(@PathVariable UUID sessionId) {
    protectionRepo.findById(sessionId).ifPresent(session -> {
      session.setActive(false);
      session.setStoppedAt(LocalDateTime.now());
      protectionRepo.save(session);
    });
    return ResponseEntity.ok().build();
  }

  @GetMapping("/alerts/count")
  public ResponseEntity<Map<String, Long>> alertsCount() {
    long count = protectionRepo.findByActiveTrue().size();
    return ResponseEntity.ok(Map.of("count", count));
  }

  @GetMapping("/alerts")
  public ResponseEntity<List<Map<String, Object>>> alerts() {
    List<ProtectionSession> active = protectionRepo.findByActiveTrue();
    active.sort(Comparator.comparingInt(ProtectionSession::getLevel).reversed());

    List<Map<String, Object>> result = new ArrayList<>();
    for (ProtectionSession session : active) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("session", session);

      orderRepo.findById(session.getOrderId()).ifPresent(order -> {
        item.put("orderId", order.getId());
        item.put("serviceType", order.getProduct() != null ? order.getProduct().getName() : "Serviço");
        item.put("orderStatus", order.getStatus());
        item.put("serviceAddress", order.getServiceAddress());

        if (order.getCustomer() != null) {
          item.put("customer", buildUserInfo(order.getCustomer()));
        }
        if (order.getProfessional() != null) {
          item.put("professional", buildUserInfo(order.getProfessional()));
        }
      });

      userRepo.findById(session.getUserId()).ifPresent(u -> item.put("activatedBy", buildUserInfo(u)));

      List<BlockedAttempt> blocked = blockedRepo.findByOrderIdOrderByBlockedAtDesc(session.getOrderId());
      item.put("blockedAttempts", blocked.size());

      result.add(item);
    }
    return ResponseEntity.ok(result);
  }

  private Map<String, Object> buildUserInfo(User u) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("id", u.getId());
    info.put("name", (u.getName() != null ? u.getName() : "") + (u.getSurname() != null ? " " + u.getSurname() : ""));
    info.put("email", u.getEmail());
    info.put("phone", u.getPhoneNumber());
    info.put("cpf", u.getCpf());
    info.put("gender", u.getGender());
    info.put("birthday", u.getBirthday());
    info.put("avatarUrl", u.getAvatarUrl());
    info.put("verified", u.getVerified());
    info.put("fullAddress", u.getFullAddress());
    return info;
  }
}
