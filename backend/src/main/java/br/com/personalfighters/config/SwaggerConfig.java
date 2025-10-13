package br.com.personalfighters.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.Collections;

@Configuration
public class SwaggerConfig {

  @Bean
  public Docket api() {
    return new Docket(DocumentationType.OAS_30)
        .select()
        .apis(RequestHandlerSelectors.basePackage("br.com.personalfighters.controller"))
        .paths(PathSelectors.any())
        .build()
        .apiInfo(apiInfo());
  }

  private ApiInfo apiInfo() {
    String title = "Personal Fighter REST API";
    String description = "REST API dos Apps e Dashboard do Personal Fighter";
    String version = "v1";
    String termsOfServiceUrl = "";
    Contact contact = new Contact(
        "Bruce",
        "www.personalfighters.com.br",
        "devcontato@personalfighters.com"
    );
    String license = "License of API";
    String licenseUrl = "License of API URL";

    return new ApiInfo(
        title,
        description,
        version,
        termsOfServiceUrl,
        contact,
        license,
        licenseUrl,
        Collections.emptyList()
    );
  }
}
