package br.com.personalfighters.repository;

import br.com.personalfighters.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {
  Optional<IdentityVerification> findFirstByUserIdOrderBySubmittedAtDesc(Long userId);
  List<IdentityVerification> findByStatusOrderBySubmittedAtAsc(String status);
  List<IdentityVerification> findTop100ByOrderBySubmittedAtDesc();
}
