package br.com.personalfighters.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_records_")
public class UserRecords {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String deviceBrand;
  private String deviceModel;
  private String deviceModelId;
  private String deviceOsName;
  private String deviceOsVersion;
  private String customerExpoPushNotificationToken;
  private String professionalExpoPushNotificationToken;

  private LocalDate createDate;
  private LocalDate lastAccessDate;

  @OneToOne(fetch = FetchType.LAZY)
  private User user;
}

