package br.com.personalfighters.service;

import br.com.personalfighters.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditService {
  void log(Long userId, String action, String entityType, String entityId, String details, String ip);
  Page<AuditLog> findAll(Pageable pageable);
}
