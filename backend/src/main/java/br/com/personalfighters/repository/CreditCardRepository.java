package br.com.personalfighters.repository;

import br.com.personalfighters.entity.CreditCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditCardRepository extends JpaRepository<CreditCard, Long> {

  Page<CreditCard> findAllByUserId(Long id, Pageable pageable);
}
