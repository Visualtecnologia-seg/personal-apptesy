package br.com.personalfighters.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/platform")
public class PlatformController {

  private final br.com.personalfighters.service.AsaasService asaasService;

  public PlatformController(br.com.personalfighters.service.AsaasService asaasService) {
    this.asaasService = asaasService;
  }

  @GetMapping("/config")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> getConfig() {
    return Map.of(
        "platformFee", 5.0,
        "platformFeeLabel", "5%",
        "platformFeeDescription", "A plataforma retém 5% de taxa sobre cada serviço concluído.",
        "testPayments", asaasService.isTestMode(),
        "cancelPricePerKm", 1.5,
        "cancelMinFee", 10
    );
  }
}
