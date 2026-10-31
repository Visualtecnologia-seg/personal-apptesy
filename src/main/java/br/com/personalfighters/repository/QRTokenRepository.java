package br.com.personalfighters.repository;

import br.com.personalfighters.entity.QRToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QRTokenRepository extends JpaRepository<QRToken, Long> {

  Optional<QRToken> findByCode(String code);

  Optional<QRToken> findByOrderIdAndTypeAndUsedFalse(UUID orderId, String type);

  @Modifying
  @Transactional
  @Query("DELETE FROM QRToken t WHERE t.expiresAt < :now")
  void deleteExpired(LocalDateTime now);
}
