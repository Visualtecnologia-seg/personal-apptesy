package br.com.personalfighters.service;

import br.com.personalfighters.entity.CreditCard;
import br.com.personalfighters.model.pagarme.request.CardUnsafeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CreditCardService {

  Optional<CreditCard> findById(Long id);

  CreditCard save(Long id, CardUnsafeRequest creditCard);

  List<CreditCard> findAll(Long id);

  void delete(Long id);

  Page<CreditCard> findAllByUser(Long id, Pageable pageable);

}
