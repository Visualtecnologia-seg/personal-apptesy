package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Address;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.AddressRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.AddressService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

  private final AddressRepository addressRepository;
  private final UserRepository userRepository;

  @Override
  public Page<Address> findAll(Address address, Pageable pageable) {
    Example<Address> example = Example.of(address,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    );
    return addressRepository.findAll(example, pageable);
  }

  @Override
  public Page<Address> findAllByUser(Long id, Pageable pageable) {
    User user = userRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("User not found"));
    return addressRepository.findAllByUserAndActiveIsTrue(user, pageable);
  }

  @Override
  public Address findById(Long id) {
    return addressRepository.findById(id).orElseThrow();
  }

  @Override
  public Address save(Long id, Address address) {
    User user = userRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("User not found"));
    address.setUser(user);
    address.setActive(true);
    return addressRepository.save(address);
  }

  @Override
  public void delete(Long id) throws Exception {
    Address address = addressRepository.getOne(id);
    address.setActive(false);
    addressRepository.save(address);
  }
}
