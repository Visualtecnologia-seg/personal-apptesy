package br.com.personalfighters.repository;

import br.com.personalfighters.entity.SpecialtyHistory;
import br.com.personalfighters.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecialtyHistoryRepository extends JpaRepository<SpecialtyHistory, Long> {
  List<SpecialtyHistory> findByUserOrderByChangedAtDesc(User user);
}
