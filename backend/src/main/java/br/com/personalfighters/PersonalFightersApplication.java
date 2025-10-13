package br.com.personalfighters;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.TimeZone;

@EnableScheduling
@EnableCaching
@SpringBootApplication
public class PersonalFightersApplication {

  public static void main(String[] args) {
    SpringApplication.run(PersonalFightersApplication.class, args);
  }

  @PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));   // It will set UTC timezone
    System.out.println("Spring boot application running in UTC timezone: " + new Date());   // It will print UTC timezone
  }

}
