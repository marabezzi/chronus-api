package br.com.atom.chronus_api.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Service;

import br.com.atom.chronus_api.config.IdClassConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Realiza o login no relógio iDClass e retorna o session token.
 *
 * Endpoint: POST /login.fcgi
 * Body:     { "login": "admin", "password": "admin" }
 * Retorno:  { "session": "abc123" }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdClassAuthService {

    private final IdClassConfig idClassConfig;
    private final HttpClient    httpClient;
    private final ObjectMapper  mapper = new ObjectMapper();

    /**
     * Faz login no relógio e retorna o session token.
     *
     * @return token de sessão ou null se falhar
     */
    public String login() {
        try {
            String url  = idClassConfig.getBaseUrl() + "/login.fcgi";
            String body = mapper.writeValueAsString(java.util.Map.of(
                    "login",    idClassConfig.getUser(),
                    "password", idClassConfig.getPassword()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Login falhou: HTTP {}", response.statusCode());
                return null;
            }

            JsonNode json = mapper.readTree(response.body());

            if (json.has("session")) {
                String session = json.get("session").asString();
                log.info("Login realizado com sucesso. Session: {}...",
                        session.substring(0, Math.min(8, session.length())));
                return session;
            }

            log.error("Login falhou: resposta sem 'session'. Body: {}",
                    response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro ao fazer login no iDClass: {}", e.getMessage());
            return null;
        }
    }
}