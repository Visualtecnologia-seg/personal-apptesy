package br.com.personalfighters.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Redireciona rotas desconhecidas para o index.html do painel admin (React SPA).
 * As rotas da API (ex: /auth, /users, /orders) são interceptadas antes deste controller.
 */
@Controller
public class SpaController {

    /** Painel Admin (React dashboard) */
    @RequestMapping(value = {
        "/dashboard",
        "/dashboard/**",
        "/admin",
        "/admin/**",
        "/login",
        "/home"
    })
    public String forwardDashboard() {
        return "forward:/index.html";
    }

    /** App Cliente — raiz e rotas sem extensão */
    @RequestMapping(value = {"/app", "/app/"})
    public String forwardClientAppRoot() {
        return "forward:/app/index.html";
    }

}
