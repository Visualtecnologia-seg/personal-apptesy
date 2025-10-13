package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum AccountType {
  CURRENT_ACCOUNT("CURRENT_ACCOUNT"), SAVING_ACCOUNT("SAVING_ACCOUNT");
  private final String accountType;
}