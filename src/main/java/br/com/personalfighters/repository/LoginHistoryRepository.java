package br.com.personalfighters.repository;

import br.com.personalfighters.entity.LoginHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {
  List<LoginHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
