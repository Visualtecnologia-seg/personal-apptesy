package br.com.personalfighters.repository;

import br.com.personalfighters.entity.UrgentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UrgentRequestRepository extends JpaRepository<UrgentRequest, UUID> {
  List<UrgentRequest> findByStatusAndExpiresAtAfter(String status, LocalDateTime now);
  Optional<UrgentRequest> findFirstByCustomerIdOrderByCreatedAtDesc(Long customerId);

  /** Aceite atômico: só funciona se o chamado ainda estiver aberto. Retorna 1 se este profissional ganhou. */
  @Modifying
  @Query("UPDATE UrgentRequest u SET u.status = 'TAKEN', u.takenBy = :pro, u.takenAt = :now " +
      "WHERE u.id = :id AND u.status = 'OPEN' AND u.expiresAt > :now")
  int take(@Param("id") UUID id, @Param("pro") Long professionalId, @Param("now") LocalDateTime now);
}
