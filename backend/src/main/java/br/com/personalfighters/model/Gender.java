package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Gender {
  MALE("MALE"), FEMALE("FEMALE"), ANY("ANY");
  private final String gender;
}
