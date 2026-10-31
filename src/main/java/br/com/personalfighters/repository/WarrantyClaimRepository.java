package br.com.personalfighters.repository;

import br.com.personalfighters.entity.WarrantyClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, UUID> {
  Optional<WarrantyClaim> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
  List<WarrantyClaim> findByProfessionalIdOrderByCreatedAtDesc(Long professionalId);
  List<WarrantyClaim> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
