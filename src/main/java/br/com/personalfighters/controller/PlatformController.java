package br.com.personalfighters.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/platform")
public class PlatformController {

  private final br.com.personalfighters.service.AsaasService asaasService;
  private final br.com.personalfighters.service.SmsService smsService;

  public PlatformController(br.com.personalfighters.service.AsaasService asaasService, br.com.personalfighters.service.SmsService smsService) {
    this.asaasService = asaasService;
    this.smsService = smsService;
  }

  @GetMapping("/config")
  @ResponseStatus(HttpStatus.OK)
  public Map<String, Object> getConfig() {
    double pct = br.com.personalfighters.util.Pricing.FEE_RATE.doubleValue() * 100;
    String label = (pct == Math.rint(pct) ? String.valueOf((int) pct) : String.valueOf(pct).replace('.', ',')) + "%";
    Map<String, Object> m = new java.util.LinkedHashMap<>();
    m.put("platformFee", pct);
    m.put("platformFeeLabel", label);
    m.put("platformFeeDescription", "A plataforma retém " + label + " de taxa sobre cada serviço concluído.");
    m.put("testPayments", asaasService.isTestMode());
    // Os apps escondem "entrar por SMS" e a confirmação por SMS no cadastro enquanto o SMS não estiver ligado
    m.put("sms", !smsService.isTestMode());
    m.put("cancelPricePerKm", br.com.personalfighters.service.TripService.PRICE_PER_KM);
    m.put("cancelMinFee", br.com.personalfighters.service.TripService.MIN_FEE);
    m.put("pointsPerService", br.com.personalfighters.service.PointsAwarder.POINTS_PER_SERVICE);
    m.put("minOrderForRedeem", br.com.personalfighters.controller.EngagementController.MIN_ORDER_FOR_REDEEM);
    return m;
  }
}
