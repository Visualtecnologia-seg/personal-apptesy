package br.com.personalfighters.service;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;

public interface UserRecordsService {
  void save(UserRecords userRecords);

  UserRecords findByUser(User user);
}
