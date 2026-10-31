package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Customer;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

  @Query("select c from Customer c where c.id =:id")
  Page<User> findAllFavoritesByCustomerId(Long id, Pageable pageable);
}
