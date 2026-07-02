package br.com.atom.chronus_api.service;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.atom.chronus_api.config.IdClassConfig;
import br.com.atom.chronus_api.dtos.FuncionarioResponseDTO;
import br.com.atom.chronus_api.dtos.UserDTO;
import br.com.atom.chronus_api.dtos.UsersResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
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


    // ── Listar funcionários (AFD) ─────────────────────────────────────────────

    public List<FuncionarioResponseDTO> listarFuncionarios() {
        try {
            String session = sessionManager.getSession();
            if (session == null) {
                log.error("Sem sessão válida para listar funcionários.");
                return Collections.emptyList();
            }

            HttpResponse<String> response = executarRequisicao(
                    montarBodyAfd(session), session, "/get_afd.fcgi");
            log.info("HTTP STATUS: {}", response.statusCode());

            if (response.statusCode() == 200) {
                log.info("AFD recebido ({} bytes)", response.body().length());
                return afdParser.parseFuncionarios(response.body());
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return listarFuncionariosComRetry();
            }

            log.error("Erro ao buscar AFD. HTTP {}: {}", response.statusCode(), response.body());
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Erro de comunicação: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<FuncionarioResponseDTO> listarFuncionariosComRetry() throws Exception {
        log.warn("Sessão rejeitada. Renovando e retentando...");
        sessionManager.renovarTokenForcado();

        String novaSession = sessionManager.getSession();
        if (novaSession == null) {
            log.error("Não foi possível renovar a sessão.");
            return Collections.emptyList();
        }

        HttpResponse<String> retry = executarRequisicao(
                montarBodyAfd(novaSession), novaSession, "/get_afd.fcgi");

        if (retry.statusCode() == 200) {
            log.info("AFD recebido após retry ({} bytes)", retry.body().length());
            return afdParser.parseFuncionarios(retry.body());
        }

        log.error("Erro após renovar sessão. HTTP {}: {}", retry.statusCode(), retry.body());
        return Collections.emptyList();
    }


    // ── Importar empregados do relógio (/load_users.fcgi) ────────────────────

    public UsersResponseDTO importarEmpregados() {
        try {
            String session = sessionManager.getSession();
            if (session == null) {
                log.error("Sem sessão válida para importar empregados.");
                return null;
            }

            String bodyJson = montarBodyUsers(session);
            log.info("Importando empregados — body: [{}]", bodyJson);

            HttpResponse<String> response = executarRequisicao(bodyJson, session, "/load_users.fcgi");
            log.info("HTTP STATUS: {}", response.statusCode());

            if (response.statusCode() == 200) {
                log.info("Empregados recebidos ({} bytes)", response.body().length());
                UsersResponseDTO result = objectMapper.readValue(response.body(), UsersResponseDTO.class);
                log.info("Total de empregados importados: {}", result.getCount());
                return result;
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando e retentando...", response.statusCode());
                sessionManager.renovarTokenForcado();

                String novaSession = sessionManager.getSession();
                if (novaSession == null) {
                    log.error("Não foi possível renovar a sessão.");
                    return null;
                }

                HttpResponse<String> retry = executarRequisicao(
                        montarBodyUsers(novaSession), novaSession, "/load_users.fcgi");

                if (retry.statusCode() == 200) {
                    log.info("Empregados recebidos após retry ({} bytes)", retry.body().length());
                    UsersResponseDTO result = objectMapper.readValue(retry.body(), UsersResponseDTO.class);
                    log.info("Total de empregados importados após retry: {}", result.getCount());
                    return result;
                }

                log.error("Erro após renovar sessão. HTTP {}: {}", retry.statusCode(), retry.body());
                return null;
            }

            log.error("Erro ao importar empregados. HTTP {}: {}", response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro de comunicação ao importar empregados: {}", e.getMessage(), e);
            return null;
        }
    }


    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String montarBodyAfd(String session) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        body.put("limit", 100);
        body.put("offset", 0);
        return objectMapper.writeValueAsString(body);
    }

    private String montarBodyUsers(String session) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("limit", 100);
        body.put("offset", 0);
        body.put("session", session);
        return objectMapper.writeValueAsString(body);
    }

    private HttpResponse<String> executarRequisicao(String bodyJson, String session, String endpoint)
            throws IOException, InterruptedException {
        String url = config.getBaseUrl() + endpoint;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Cookie", authService.buildCookie(session))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

}