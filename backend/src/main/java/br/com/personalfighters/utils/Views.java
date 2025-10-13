package br.com.personalfighters.utils;

public class Views {
  public static class FirstLevel {
  }

  public static class SecondLevel extends FirstLevel {
  }

  public static class ThirdLevel extends SecondLevel {
  }

  public static class Product {
    public static class Basic {
    }

    public static class Default extends Basic {
    }

    public static class Extended extends Default {
    }
  }

  public static class ProfessionalView {
    public static class FirstLevel {
    }

    public static class SecondLevel extends FirstLevel {
    }

    public static class ThirdLevel extends SecondLevel {
    }
  }

  public static class CustomerView {
    public static class FirstLevel {
    }

    public static class SecondLevel extends FirstLevel {
    }

    public static class ThirdLevel extends SecondLevel {
    }

    public static class Favorite {
    }

  }

  public static class User {
    public static class Basic {
    }

    public static class Extended extends Basic {
    }

    public static class Security extends Extended {
    }

    // TODO Necessário?
    public static class Customer extends Basic {
    }

    public static class Professional extends Basic {
    }

  }

  public static class Customer {
    public static class Basic {
    }
  }

  public static class OrderView {
    public static class FirstLevel {
    }

    public static class SecondLevel extends FirstLevel {
    }

    public static class ThirdLevel extends SecondLevel {
    }
  }

  public static class AgendaView {
    public static class FirstLevel {
    }

    public static class SecondLevel extends FirstLevel {
    }

    public static class ThirdLevel extends SecondLevel {
    }
  }

  public static class FinanceView {
    public static class FirstLevel {
    }

    public static class SecondLevel extends FirstLevel {
    }

    public static class ThirdLevel extends SecondLevel {
    }
  }

  public static class CreditCard {
    public static class NewCard {
    }
  }
}
