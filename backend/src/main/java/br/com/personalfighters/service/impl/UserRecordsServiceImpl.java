package br.com.personalfighters.service.impl;

import br.com.personalfighters.entity.User;
import br.com.personalfighters.entity.UserRecords;
import br.com.personalfighters.repository.UserRecordsRepository;
import br.com.personalfighters.service.UserRecordsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserRecordsServiceImpl implements UserRecordsService {

  private final UserRecordsRepository userRecordsRepository;

  @Override
  public void save(UserRecords userRecords) {
    UserRecords records = userRecordsRepository.findByUser(userRecords.getUser());
    if (!ObjectUtils.isEmpty(records)) {
      records.setCreateDate(records.getCreateDate());
      userRecords.setLastAccessDate(LocalDate.now());
      userRecords.setId(records.getId());
      if (ObjectUtils.isEmpty(userRecords.getCustomerExpoPushNotificationToken())) {
        userRecords.setCustomerExpoPushNotificationToken(records.getCustomerExpoPushNotificationToken());
      }
      if (ObjectUtils.isEmpty(userRecords.getProfessionalExpoPushNotificationToken())) {
        userRecords.setProfessionalExpoPushNotificationToken(records.getProfessionalExpoPushNotificationToken());
      }
    }
    userRecordsRepository.save(userRecords);
  }

  @Override
  public UserRecords findByUser(User user) {
    return userRecordsRepository.findByUser(user);
  }
}
