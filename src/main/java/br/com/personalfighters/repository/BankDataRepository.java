package br.com.personalfighters.repository;

import br.com.personalfighters.entity.BankData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BankDataRepository extends JpaRepository<BankData, Long> {

  @Query("select b from BankData b where b.professional.id=:id")
  BankData findByProfessionalId(Long id);

}
