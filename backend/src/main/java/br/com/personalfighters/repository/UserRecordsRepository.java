package br.com.personalfighters.repository;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRecordsRepository extends JpaRepository<UserRecords, Long> {

  UserRecords findByUser(User user);
}
