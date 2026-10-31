package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Professional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

  Professional findByReference(String ref);
}
