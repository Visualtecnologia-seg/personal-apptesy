package br.com.personalfighters.service;

import br.com.personalfighters.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AddressService {
  Page<Address> findAll(Address address, Pageable pageable);

  Page<Address> findAllByUser(Long id, Pageable pageable);

  Address findById(Long id);

  Address save(Long id, Address address);

  void delete(Long id) throws Exception;


}
