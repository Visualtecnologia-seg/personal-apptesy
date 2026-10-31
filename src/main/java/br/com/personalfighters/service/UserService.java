package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

  User findById(Long id);

  User findAuthUser();

  User setUserAdmin(User user);

  User findByUsername(String username);

  User findByEmail(String email);

  User findByReference(String ref);

  User save(User user);

  User update(User user);

  void delete(Long id);

  Page<User> findAll(User user, Pageable pageRequest);

  Boolean checkIfExists(String email);

  User verifyUser(Long id, String documentUrl);

  User rejectVerification(Long id, String reason);

  User requestVerification(Long id, java.util.Map<String, String> payload);

  User updateLocation(Long id, double lat, double lng);

  java.util.List<User> nearbyProfessionals(double lat, double lng, double radiusKm);
}
