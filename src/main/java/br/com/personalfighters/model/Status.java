package br.com.personalfighters.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum Status {
  OPEN("OPEN"), CONFIRMED("CONFIRMED"), AWAITING_PAYMENT("AWAITING_PAYMENT"),
  IN_PROGRESS("IN_PROGRESS"), PENDING_CLIENT_CONFIRMATION("PENDING_CLIENT_CONFIRMATION"),
  DONE("DONE"), REVIEWED("REVIEWED"),
  BLOCKED("BLOCKED"), CANCELLED("CANCELLED");
  private final String status;
}
