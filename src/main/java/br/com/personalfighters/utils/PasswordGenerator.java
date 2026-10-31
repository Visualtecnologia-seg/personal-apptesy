package br.com.personalfighters.utils;

import java.util.Random;

public class PasswordGenerator {

  private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
  private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String NUMBERS = "0123456789";
  private static final String SPECIAL = "!@#$%&*()_+-=[]|,./?><";

  public static String GeneratePassword(int length) {
    String combinedChars = UPPER + LOWER + SPECIAL + NUMBERS;
    Random random = new Random();
    char[] password = new char[length];

    password[0] = LOWER.charAt(random.nextInt(LOWER.length()));
    password[1] = UPPER.charAt(random.nextInt(UPPER.length()));
    password[2] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));
    password[3] = NUMBERS.charAt(random.nextInt(NUMBERS.length()));

    for (int i = 4; i < length; i++) {
      password[i] = combinedChars.charAt(random.nextInt(combinedChars.length()));
    }
    return new String(password);
  }

  public static String GenerateWeakPassword(int length) {
    Random random = new Random();
    String n1 = String.valueOf(random.nextInt(((9 - 1) + 1) + 1));
    String n2 = String.valueOf(random.nextInt(((9 - 1) + 1) + 1));
    String n3 = String.valueOf(random.nextInt(((9 - 1) + 1) + 1));
    String n4 = String.valueOf(random.nextInt(((9 - 1) + 1) + 1));
    return n1 + n2 + n3 + n4;
  }
}
