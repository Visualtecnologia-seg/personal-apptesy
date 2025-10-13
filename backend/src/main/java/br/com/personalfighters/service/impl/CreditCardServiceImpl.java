package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.CreditCard;
import br.com.personalfighters.entity.Finance;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.model.pagarme.request.CardUnsafeRequest;
import br.com.personalfighters.model.pagarmev5.CardResponse;
import br.com.personalfighters.model.pagarmev5.request.CreateCardRequest;
import br.com.personalfighters.repository.CreditCardRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.CreditCardService;
import br.com.personalfighters.service.FinanceService;
import br.com.personalfighters.service.PagarmeService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditCardServiceImpl implements CreditCardService {

  private final CreditCardRepository creditCardRepository;
  private final UserRepository userRepository;
  private final PagarmeService pagarmeService;
  private final FinanceService financeService;

  @Override
  public Optional<CreditCard> findById(Long id) {
    return Optional.empty();
  }

  @Override
  public CreditCard save(Long id, CardUnsafeRequest creditCard) {

    User user = userRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("User not found"));

    //Pegando a finance do User
    Finance userFinance = financeService.findByUser(user.getId());

    //Setando o customerId com o customerPaymentProfile
    creditCard.setCustomer_id(userFinance.getCustomerPaymentProfile());
    creditCard.setCard_number(creditCard.getCard_number().replaceAll(" ", ""));
    creditCard.setCard_expiration_date(creditCard.getCard_expiration_date().replaceAll("/", ""));
    creditCard.getBilling().setCountry("BR");

    var convertedCard = CreateCardRequest.convertToCardRequest(creditCard);

    //Salvando o cartão no Pagarme
    CardResponse paymentCard = pagarmeService.saveCardUnsafe(creditCard.getCustomer_id(), convertedCard);

    //Build do credit card com o ID no PagarMe, Name do proprietário e somente os últimos números do cartão e salvando no DB
    CreditCard newCard = CreditCard
        .builder()
        .paymentServiceId(paymentCard.getId())
        .name(paymentCard.getHolderName())
        .lastNumbers(paymentCard.getLastFourDigits())
        .brand(paymentCard.getBrand())
        .user(user)
        .street(creditCard.getBilling().getStreet())
        .streetNumber(creditCard.getBilling().getStreet_number())
        .neighborhood(creditCard.getBilling().getNeighborhood())
        .zipcode(creditCard.getBilling().getZipcode())
        .city(creditCard.getBilling().getCity())
        .state(creditCard.getBilling().getState())
        .country(creditCard.getBilling().getCountry())
        .build();

    return creditCardRepository.save(newCard);
  }

  @Override
  public List<CreditCard> findAll(Long id) {
    return creditCardRepository.findAll();
  }

  @Override
  public void delete(Long id) {
    creditCardRepository.deleteById(id);
  }

  @Override
  public Page<CreditCard> findAllByUser(Long id, Pageable pageable) {
    return creditCardRepository.findAllByUserId(id, pageable);
  }
}
