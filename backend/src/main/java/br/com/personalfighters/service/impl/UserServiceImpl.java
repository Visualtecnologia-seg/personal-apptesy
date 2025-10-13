package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.*;
import br.com.personalfighters.model.AccountType;
import br.com.personalfighters.model.Role;
import br.com.personalfighters.model.pagarme.request.CustomerPagarmeRequest;
import br.com.personalfighters.model.pagarme.request.DocumentRequest;
import br.com.personalfighters.model.pagarmev5.CustomerResponse;
import br.com.personalfighters.model.pagarmev5.request.CustomerRequest;
import br.com.personalfighters.repository.BankDataRepository;
import br.com.personalfighters.repository.FinanceRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.PagarmeService;
import br.com.personalfighters.service.ProfessionalAgendaService;
import br.com.personalfighters.service.UserService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final FinanceRepository financeRepository;
  private final BankDataRepository bankDataRepository;
  private final ProfessionalAgendaService professionalAgendaService;
  private final PagarmeService pagarmeService;

  @Override
  public User findById(Long id) {
    return userRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("Profissional não encontrado - ID: " + id));
  }

  @Override
  public User findAuthUser() {
    UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    return findByUsername(userDetails.getUsername());
  }

  @Override
  public User setUserAdmin(User user) {
    if (!user.getRole().contains(Role.ADMIN)) {
      List<Role> roles = user.getRole();
      roles.add(Role.ADMIN);
      user.setRole(roles);
    } else {
      throw new RuntimeException("User is already ADMIN");
    }
    return update(user);
  }

  @Override
  public User findByUsername(String username) {
    User user = userRepository.findByUsername(username);
    if (user == null) {
      throw new ObjectNotFoundException("User not found");
    }
    return user;
  }

  @Override
  public User findByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  @Override
  public User findByReference(String ref) {
    return userRepository.findByProfessionalReference(ref);
  }

  @Override
  @Transactional
  public User save(User user) {
    if (userRepository.existsByEmail(user.getEmail())) {
      throw new RuntimeException("User already exists");
    }
    String passwordHash = new BCryptPasswordEncoder().encode(user.getPassword());
    user.setPassword(passwordHash);
    user.setActive(true);
    user.setRole(Arrays.asList(Role.CUSTOMER, Role.PROFESSIONAL));

    Professional professional = new Professional();
    professional.setActive(true);
    user.setProfessional(professional);

    Customer customer = new Customer();
    customer.setActive(true);
    user.setCustomer(customer);

    userRepository.save(user);

    /* Agenda do profissional */
    professionalAgendaService.createProfessionalAgenda(user);

    /* Finanças do usuário */
    Finance finance = new Finance();
    finance.setCustomerBalance(BigDecimal.ZERO);
    finance.setProfessionalBalance(BigDecimal.ZERO);
    finance.setUser(user);

    /* Conta no PagarMe */
    CustomerResponse customerPagarme = pagarmeService.saveCustomer(CustomerRequest.convertTo(user));
    finance.setCustomerPaymentProfile(customerPagarme.getId());
    financeRepository.save(finance);

    /* Dados bancários do profissional */
    BankData bankData = new BankData();
    bankData.setProfessional(user);
    bankData.setAccountType(AccountType.CURRENT_ACCOUNT);
    bankDataRepository.save(bankData);

    return user;
  }

  @Override
  public User update(User user) {
    User u = findById(user.getId());
    user.setUsername(u.getUsername());
    user.setPassword(u.getPassword());
    return userRepository.save(user);
  }

  @Override
  public void delete(Long id) {
    User user = userRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("User not found"));
    // TODO remover o usuário da lista do product professionals
    user.setActive(false);
    userRepository.save(user);
  }


  @Override
  public Page<User> findAll(User user, Pageable pageable) {
    if (Objects.nonNull(user.getName())) {
      user.setName(user.getName().toLowerCase());
    }
    return userRepository.findAllUsersByQuery(user, pageable);
  }

  @Override
  public Boolean checkIfExists(String email) {
    return userRepository.existsByEmail(email);
  }

  private CustomerPagarmeRequest convertToCustomerToPagarme(User user) {
    String phoneNumber = "";
    phoneNumber = "+55" + user.getPhoneNumber().replaceAll("-", "");
    phoneNumber = phoneNumber.replaceAll(" ", "");
    phoneNumber = phoneNumber.replaceAll("[()]", "");

    return CustomerPagarmeRequest
        .builder()
        .externalId(user.getId().toString())
        .name(user.getName())
        .type("individual") //Quando for aceitar PJ alterar para receber 'corporation'
        .country("br") //TODO verificar a necessidade de colocar outros países
        .email(user.getEmail())
        .birthday(user.getBirthday().toString())
        .documents(Collections.singletonList(DocumentRequest
            .builder()
            .type("cpf")
            .number(user.getCpf())
            .build()))
        .phoneNumbers(Collections.singletonList(phoneNumber))
        .build();
  }
}
