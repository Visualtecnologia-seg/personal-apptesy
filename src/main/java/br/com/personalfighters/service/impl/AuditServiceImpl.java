package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.AuditLog;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.AuditLogRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

  private final AuditLogRepository auditLogRepository;
  private final UserRepository userRepository;

  @Override
  public void log(Long userId, String action, String entityType, String entityId, String details, String ip) {
    try {
      User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
      AuditLog entry = AuditLog.builder()
          .user(user)
          .action(action)
          .entityType(entityType)
          .entityId(entityId)
          .details(details)
          .ipAddress(ip)
          .build();
      auditLogRepository.save(entry);
    } catch (Exception e) {
      log.warn("Falha ao registar auditoria: {}", e.getMessage());
    }
  }

  @Override
  public Page<AuditLog> findAll(Pageable pageable) {
    return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
  }
}
