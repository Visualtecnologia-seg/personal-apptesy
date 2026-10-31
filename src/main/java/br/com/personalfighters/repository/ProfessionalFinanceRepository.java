package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Finance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalFinanceRepository extends JpaRepository<Finance, Long> {

  // Finance findByProfessional(Professional professional);
}
