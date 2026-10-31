package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.Favorite;
import br.com.personalfighters.entity.User;
import br.com.personalfighters.repository.FavoriteRepository;
import br.com.personalfighters.repository.UserRepository;
import br.com.personalfighters.service.FavoriteService;
import br.com.personalfighters.service.impl.exceptions.ObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

  private final FavoriteRepository favoriteRepository;
  private final UserRepository userRepository;

  @Override
  public Page<Favorite> findAll(Favorite favorite, Pageable pageable) {
    Example<Favorite> example = Example.of(favorite,
        ExampleMatcher
            .matching()
            .withIgnoreCase()
            .withIgnoreNullValues()
            .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    );
    return favoriteRepository.findAll(example, pageable);
  }

  @Override
  public Page<Favorite> findAllByUser(Long id, Pageable pageable) {
    User user = userRepository.findById(id).orElseThrow(() -> new ObjectNotFoundException("User not found"));
    return favoriteRepository.findAllByUser(user, pageable);
  }

  @Override
  public Optional<Favorite> findById(Long id) {
    return favoriteRepository.findById(id);
  }

  @Override
  public Favorite save(Favorite favorite, String ref) {
    User professional = userRepository.findByProfessionalReference(ref);
    boolean hasFavorite = favoriteRepository.countByUserAndProfessional(favorite.getUser(), professional) > 0;
    if (hasFavorite || professional == null) {
      return null;
    } else {
      favorite.setProfessional(professional);
      return favoriteRepository.save(favorite);
    }
  }

  @Override
  public void delete(Long id) throws Exception {
    favoriteRepository.deleteById(id);
  }
}
