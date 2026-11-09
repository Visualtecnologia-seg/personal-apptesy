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
import br.com.personalfighters.repository.SpecialtyHistoryRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.EmailService;
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
  private final EmailService emailService;
  private final SpecialtyHistoryRepository specialtyHistoryRepository;

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
      throw new RuntimeException("Utilizador já é administrador");
    }
    return update(user);
  }

  @Override
  public User findByUsername(String username) {
    User user = userRepository.findByUsername(username);
    // Permite entrar também com o e-mail
    if (user == null && username != null && username.contains("@")) {
      user = userRepository.findByEmail(username.trim());
    }
    if (user == null) {
      throw new ObjectNotFoundException("Utilizador não encontrado");
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
    if (user.getCpf() == null || user.getCpf().isBlank()) {
      throw new RuntimeException("CPF é obrigatório para o cadastro");
    }
    String cpfDigits = user.getCpf().replaceAll("[^0-9]", "");
    if (cpfDigits.length() != 11) {
      throw new RuntimeException("CPF inválido. Use o formato 000.000.000-00");
    }
    if (user.getEmail() == null || user.getEmail().isBlank()) {
      throw new RuntimeException("E-mail é obrigatório para o cadastro");
    }
    if (user.getName() == null || user.getName().isBlank()) {
      throw new RuntimeException("Nome é obrigatório para o cadastro");
    }
    String invalidPassword = br.com.personalfighters.util.PasswordRules.check(user.getPassword());
    if (invalidPassword != null) throw new br.com.personalfighters.service.impl.exceptions.DataIntegrityException(invalidPassword);
    if (user.getUsername() == null || user.getUsername().isBlank() || user.getUsername().length() > 40
        || user.getUsername().contains(":") || user.getUsername().toLowerCase().startsWith("painel")) {
      throw new br.com.personalfighters.service.impl.exceptions.DataIntegrityException("Nome de usuário inválido. Use letras, números, ponto ou traço.");
    }
    if (userRepository.existsByUsername(user.getUsername())) {
      throw new RuntimeException("Este nome de usuário já está em uso. Escolha outro.");
    }
    if (userRepository.existsByEmail(user.getEmail())) {
      throw new RuntimeException("Este e-mail já está cadastrado. Tente com outro e-mail.");
    }
    user.setVerified(false);
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
    try {
      CustomerResponse customerPagarme = pagarmeService.saveCustomer(CustomerRequest.convertTo(user));
      finance.setCustomerPaymentProfile(customerPagarme.getId());
    } catch (Exception e) {
      log.warn("PagarMe customer creation skipped (missing profile data): {}", e.getMessage());
    }
    financeRepository.save(finance);

    try {
      emailService.sendWelcome(user.getEmail(), user.getName());
    } catch (Exception e) {
      log.warn("E-mail de boas-vindas não enviado: {}", e.getMessage());
    }

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
    if (user.getCpf() == null || user.getCpf().isBlank()) user.setCpf(u.getCpf());
    if (user.getEmail() == null || user.getEmail().isBlank()) user.setEmail(u.getEmail());
    if (user.getName() == null || user.getName().isBlank()) user.setName(u.getName());
    if (user.getActive() == null) user.setActive(u.getActive());
    if (user.getRole() == null || user.getRole().isEmpty()) user.setRole(u.getRole());

    // Save specialty history if specialties changed
    try {
      String newSpecialties = user.getProfessional() != null ? user.getProfessional().getSpecialties() : null;
      String oldSpecialties = u.getProfessional() != null ? u.getProfessional().getSpecialties() : null;
      boolean changed = (newSpecialties != null && !newSpecialties.equals(oldSpecialties))
          || (newSpecialties == null && oldSpecialties != null);
      if (changed) {
        SpecialtyHistory history = SpecialtyHistory.builder()
            .user(u)
            .specialties(newSpecialties)
            .build();
        specialtyHistoryRepository.save(history);
      }
    } catch (Exception e) {
      log.warn("Não foi possível salvar histórico de especialidades: {}", e.getMessage());
    }

    return userRepository.save(user);
  }

  @Override
  public void delete(Long id) {
    User user = userRepository.findById(id).orElseThrow(() ->
        new ObjectNotFoundException("Utilizador não encontrado"));
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

  @Override
  public User verifyUser(Long id, String documentUrl) {
    User user = findById(id);
    user.setVerified(true);
    user.setVerificationStatus("APPROVED");
    if (documentUrl != null && !documentUrl.isBlank()) {
      user.setDocumentUrl(documentUrl);
    }
    return userRepository.save(user);
  }

  @Override
  public User rejectVerification(Long id, String reason) {
    User user = findById(id);
    user.setVerified(false);
    user.setVerificationStatus("REJECTED");
    return userRepository.save(user);
  }

  @Override
  public User requestVerification(Long id, java.util.Map<String, String> payload) {
    User user = findById(id);
    if (payload.containsKey("fullName") && !payload.get("fullName").isBlank()) {
      user.setName(payload.get("fullName"));
    }
    if (payload.containsKey("birthday") && !payload.get("birthday").isBlank()) {
      try {
        user.setBirthday(java.time.LocalDate.parse(payload.get("birthday")));
      } catch (Exception ignored) {}
    }
    if (payload.containsKey("cpf") && !payload.get("cpf").isBlank()) {
      user.setCpf(payload.get("cpf").replaceAll("[^0-9]", ""));
    }
    if (payload.containsKey("phoneNumber") && !payload.get("phoneNumber").isBlank()) {
      user.setPhoneNumber(payload.get("phoneNumber"));
    }
    if (payload.containsKey("fullAddress") && !payload.get("fullAddress").isBlank()) {
      user.setFullAddress(payload.get("fullAddress"));
    }
    if (payload.containsKey("docFrontUrl") && !payload.get("docFrontUrl").isBlank()) {
      user.setDocFrontUrl(payload.get("docFrontUrl"));
    }
    if (payload.containsKey("docBackUrl") && !payload.get("docBackUrl").isBlank()) {
      user.setDocBackUrl(payload.get("docBackUrl"));
    }
    if (payload.containsKey("docSelfieUrl") && !payload.get("docSelfieUrl").isBlank()) {
      user.setDocSelfieUrl(payload.get("docSelfieUrl"));
    }
    user.setVerificationStatus("PENDING");
    user.setVerified(false);
    return userRepository.save(user);
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

  @Override
  public User updateLocation(Long id, double lat, double lng) {
    User user = findById(id);
    user.setLatitude(lat);
    user.setLongitude(lng);
    return userRepository.save(user);
  }

  @Override
  public java.util.List<User> nearbyProfessionals(double lat, double lng, double radiusKm) {
    Iterable<User> all = userRepository.findAll();
    java.util.List<User> result = new java.util.ArrayList<>();
    for (User u : all) {
      if (u.getProfessional() == null) continue;
      if (!Boolean.TRUE.equals(u.getActive())) continue;
      Double uLat = u.getLatitude();
      Double uLng = u.getLongitude();
      if (uLat == null || uLng == null) continue;
      double dist = haversine(lat, lng, uLat, uLng);
      if (dist <= radiusKm) result.add(u);
    }
    return result;
  }

  private double haversine(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
