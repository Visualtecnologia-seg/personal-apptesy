package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Role {

  ADMIN("ROLE_ADMIN"), CUSTOMER("ROLE_CUSTOMER"), PROFESSIONAL("ROLE_PROFESSIONAL");

  private final String role;

}
