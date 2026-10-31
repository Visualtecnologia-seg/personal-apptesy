package br.com.personalfighters.service.impl.exceptions;

public class MessageBlockedException extends RuntimeException {
  private final int attemptNumber;
  private final String matched;

  public MessageBlockedException(String message, int attemptNumber, String matched) {
    super(message);
    this.attemptNumber = attemptNumber;
    this.matched = matched;
  }

  public int getAttemptNumber() { return attemptNumber; }
  public String getMatched() { return matched; }
}
