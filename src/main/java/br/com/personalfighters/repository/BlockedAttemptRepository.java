package br.com.personalfighters.repository;

import br.com.personalfighters.entity.BlockedAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlockedAttemptRepository extends JpaRepository<BlockedAttempt, UUID> {
  long countByOrderIdAndSenderId(UUID orderId, Long senderId);
  List<BlockedAttempt> findByOrderIdOrderByBlockedAtDesc(UUID orderId);
}
