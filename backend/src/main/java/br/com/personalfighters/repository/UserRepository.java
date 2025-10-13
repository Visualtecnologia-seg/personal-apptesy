package br.com.personalfighters.repository;

import br.com.personalfighters.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  User findByUsername(String username);

  User findByEmail(String email);

  boolean existsByEmail(String email);

  User findByProfessionalReference(String ref);

  @Query(value = "select u from User u left join u.role roles where "
                      + "(:#{#user.name} is null or lower(u.name) like %:#{#user.name}%) "
                      + "and (:#{#user.username} is null or u.username = :#{#user.username}) "
                      + "and (:#{#user.email} is null or u.email like :#{#user.email}) "
                      + "and (:#{#user.gender} is null or u.gender = :#{#user.gender}) "
                      + "and ((:#{#user.role}) is null or roles in (:#{#user.role}))")
  Page<User> findAllUsersByQuery(@Param("user") User user, Pageable pageable);
}
