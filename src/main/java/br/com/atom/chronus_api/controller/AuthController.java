package br.com.atom.chronus_api.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.atom.chronus_api.dtos.SessionStatusDTO;
import br.com.atom.chronus_api.service.SessionManager;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints de autenticação com o relógio iDClass.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionManager sessionManager;

    /**
     * GET /api/auth/status
     * Retorna o status da sessão atual.
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        SessionStatusDTO s = sessionManager.getStatus();
        return ResponseEntity.ok(Map.of(
                "ativa",             s.isAtiva(),
                "segundosRestantes", s.getSegundosRestantes(),
                "mensagem",          s.getMensagem()
        ));
    }

    /**
     * POST /api/auth/login
     * Força um novo login no relógio.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login() {
        sessionManager.invalidar();
        String session = sessionManager.getSession();

        if (session != null) {
            return ResponseEntity.ok(Map.of(
                    "mensagem", "Login realizado com sucesso",
                    "status",   "OK"
            ));
        }

        return ResponseEntity.status(503).body(Map.of(
                "erro",    "Nao foi possivel autenticar no relogio",
                "status",  "ERRO"
        ));
    }

    /**
     * POST /api/auth/renovar
     * Força a renovação da sessão.
     */
    @PostMapping("/renovar")
    public ResponseEntity<?> renovar() {
        String session = sessionManager.getSession();

        if (session != null) {
            SessionStatusDTO s = sessionManager.getStatus();
            return ResponseEntity.ok(Map.of(
                    "mensagem",          "Sessao renovada",
                    "segundosRestantes", s.getSegundosRestantes()
            ));
        }

        return ResponseEntity.status(503).body(
                Map.of("erro", "Nao foi possivel renovar a sessao"));
    }
}