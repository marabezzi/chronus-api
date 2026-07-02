package br.com.atom.chronus_api.service;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Service;

import br.com.atom.chronus_api.config.IdClassConfig;
import br.com.atom.chronus_api.dtos.FuncionarioResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class FuncionariosService {

    private final IdClassConfig      config;
    private final HttpClient         httpClient;
    private final SessionManager     sessionManager;
    private final IdClassAuthService authService;
    private final ObjectMapper       objectMapper;
    private final AfdParser          afdParser;


    // ── LISTAR TODOS (parse AFD) ──────────────────────────────────────────────

    public List<FuncionarioResponseDTO> listarFuncionarios() {
        try {
            String session = sessionManager.getSession();
            if (session == null) {
                log.error("Sem sessão válida para listar funcionários.");
                return Collections.emptyList();
            }

            HttpResponse<String> response = executarRequisicao(montarBody(session), session);
            log.info("HTTP STATUS: {}", response.statusCode());

            if (response.statusCode() == 200) {
                log.info("AFD recebido ({} bytes)", response.body().length());
                return afdParser.parseFuncionarios(response.body()); // ✅ corrigido
            }

            // Sessão expirou — renova e retenta
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando e retentando...", response.statusCode());
                sessionManager.renovarTokenForcado();

                String novaSession = sessionManager.getSession();
                if (novaSession == null) {
                    log.error("Não foi possível renovar a sessão.");
                    return Collections.emptyList();
                }

                HttpResponse<String> retry = null;
                try {
                    retry = executarRequisicao(montarBody(novaSession), novaSession);
                    if (retry.statusCode() == 200) {
                        log.info("AFD recebido após retry ({} bytes)", retry.body().length());
                        return afdParser.parseFuncionarios(retry.body()); // ✅ corrigido
                    }
                } catch (Exception e) {
                    log.error("Erro ao parsear AFD após retry: {}", e.getMessage());
                    throw new RuntimeException("Falha ao processar resposta da API", e);
                }

                if (retry != null) {
                    log.error("Erro após renovar sessão. HTTP {}: {}", retry.statusCode(), retry.body());
                }
            }

            log.error("Erro ao buscar AFD. HTTP {}: {}", response.statusCode(), response.body());
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Erro de comunicação: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String montarBody(String session) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        body.put("limit", 100);
        body.put("offset", 0);
        return objectMapper.writeValueAsString(body);
    }

    private HttpResponse<String> executarRequisicao(String bodyJson, String session)
            throws IOException, InterruptedException {
        String url = config.getBaseUrl() + "/get_afd.fcgi";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Cookie", authService.buildCookie(session))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}