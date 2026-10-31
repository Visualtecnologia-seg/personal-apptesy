package br.com.personalfighters.repository;

import br.com.personalfighters.entity.Address;
import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

  Page<Address> findAllByUserAndActiveIsTrue(User user, Pageable pageable);

}
