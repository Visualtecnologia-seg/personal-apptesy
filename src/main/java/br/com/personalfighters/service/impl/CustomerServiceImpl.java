package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Customer;
import br.com.personalfighters.repository.CustomerRepository;
import br.com.personalfighters.service.CustomerService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

  private final CustomerRepository customerRepository;

  @Override
  public void setActive(Long id) {
    Customer customer = customerRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("User not found"));
    customer.setActive(false);
    customerRepository.save(customer);
  }

}
