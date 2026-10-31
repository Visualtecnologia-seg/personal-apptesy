package br.com.personalfighters.model.pagarmev5.request;

import br.com.personalfighters.entity.User;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
@Builder
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class CustomerRequest {
  private String name;
  private String email;
  private String birthdate;
  private String document;
  private String type;
  private AddressRequest address;
  private LinkedHashMap<String, String> metadata;
  private PhonesRequest phones;
  private String code;
  private String gender;
  private String documentType;

  public static CustomerRequest convertTo(User user) {
    String phoneNumber = user.getPhoneNumber();
    phoneNumber = phoneNumber.replaceAll("\\D+", "");
    var ddd = phoneNumber.substring(0, 2);
    var number = phoneNumber.substring(2);

    return CustomerRequest.builder()
        .code(user.getId().toString())
        .email(user.getEmail())
        .type("individual")
        .name(String.format("%s %s", user.getName(), user.getSurname()))
        .birthdate(user.getBirthday().toString())
        .documentType("CPF")
        .document(user.getCpf().replaceAll("\\D+", ""))
        .gender(user.getGender().getGender().toLowerCase())
        .phones(
            PhonesRequest.builder()
                .mobilePhone(
                    PhoneRequest.builder().countryCode("55").areaCode(ddd).number(number).build())
                .build())
        .build();
  }
}
