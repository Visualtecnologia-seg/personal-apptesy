package br.com.personalfighters.repository;

import br.com.personalfighters.entity.ProtectionSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProtectionSessionRepository extends JpaRepository<ProtectionSession, UUID> {
  List<ProtectionSession> findByActiveTrue();
  Optional<ProtectionSession> findByOrderIdAndUserIdAndActiveTrue(UUID orderId, Long userId);
  List<ProtectionSession> findByOrderIdAndActiveTrue(UUID orderId);
}
