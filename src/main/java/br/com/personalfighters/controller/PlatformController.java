package br.com.personalfighters.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/platform")
public class PlatformController {

  @GetMapping("/config")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> getConfig() {
    return Map.of(
        "platformFee", 5.0,
        "platformFeeLabel", "5%",
        "platformFeeDescription", "A plataforma retém 5% de taxa sobre cada serviço concluído."
    );
  }
}
