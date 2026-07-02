package br.com.atom.chronus_api.service;

import br.com.atom.chronus_api.config.IdClassConfig;
import br.com.atom.chronus_api.dtos.AfdResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatidasLogService {

    private final IdClassConfig      config;
    private final HttpClient         httpClient;
    private final SessionManager     sessionManager;
    private final IdClassAuthService authService;
    private final ObjectMapper       objectMapper;
    private final AfdParser          afdParser;     // ← parser injetado


    // ── Buscar todas as batidas ───────────────────────────────────────────────

    public AfdResponseDTO buscarBatidas(Long inicialNsr) {
        try {
            String session = sessionManager.getSession();
            if (session == null) {
                log.error("Sem sessão válida para buscar batidas.");
                return null;
            }

            String bodyJson = montarBody(session);
            log.info("Body enviado  : [{}]", bodyJson);
            log.info("Cookie enviado: [{}]", authService.buildCookie(session));

            HttpResponse<String> response = executarRequisicao(bodyJson, session);

            if (response.statusCode() == 200) {
                log.info("AFD recebido ({} bytes)", response.body().length());
                return afdParser.parseBatidas(response.body());
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando e retentando...", response.statusCode());
                sessionManager.renovarTokenForcado();

                String novaSession = sessionManager.getSession();
                if (novaSession == null) {
                    log.error("Não foi possível renovar a sessão.");
                    return null;
                }

                HttpResponse<String> retry = executarRequisicao(montarBody(novaSession), novaSession);
                if (retry.statusCode() == 200) {
                    log.info("AFD recebido após retry ({} bytes)", retry.body().length());
                    return afdParser.parseBatidas(retry.body());
                }

                log.error("Erro após renovar sessão. HTTP {}: {}", retry.statusCode(), retry.body());
                return null;
            }

            log.error("Erro ao buscar AFD. HTTP {}: {}", response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro de comunicação com o relógio: {}", e.getMessage(), e);
            return null;
        }
    }


    // ── Buscar batidas por data / período ─────────────────────────────────────

    public AfdResponseDTO buscarData(LocalDate dataInicial, LocalDate dataFinal) {
        try {
            String session = sessionManager.getSession();
            if (session == null) {
                log.error("Sem sessão válida para buscar batidas.");
                return null;
            }

            String bodyJson = dataFinal != null
                    ? montarBody3(session, dataInicial, dataFinal)
                    : montarBody2(session, dataInicial);

            log.info("Body enviado  : [{}]", bodyJson);
            log.info("Cookie enviado: [{}]", authService.buildCookie(session));

            HttpResponse<String> response = executarRequisicao(bodyJson, session);

            if (response.statusCode() == 200) {
                log.info("AFD recebido ({} bytes)", response.body().length());
                return filtrarPorPeriodo(afdParser.parseBatidas(response.body()), dataInicial, dataFinal);
            }

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                log.warn("Sessão rejeitada (HTTP {}). Renovando e retentando...", response.statusCode());
                sessionManager.renovarTokenForcado();

                String novaSession = sessionManager.getSession();
                if (novaSession == null) {
                    log.error("Não foi possível renovar a sessão.");
                    return null;
                }

                String retryBody = dataFinal != null
                        ? montarBody3(novaSession, dataInicial, dataFinal)
                        : montarBody2(novaSession, dataInicial);

                HttpResponse<String> retry = executarRequisicao(retryBody, novaSession);
                if (retry.statusCode() == 200) {
                    log.info("AFD recebido após retry ({} bytes)", retry.body().length());
                    return filtrarPorPeriodo(afdParser.parseBatidas(retry.body()), dataInicial, dataFinal);
                }

                log.error("Erro após renovar sessão. HTTP {}: {}", retry.statusCode(), retry.body());
                return null;
            }

            log.error("Erro ao buscar AFD. HTTP {}: {}", response.statusCode(), response.body());
            return null;

        } catch (Exception e) {
            log.error("Erro de comunicação com o relógio: {}", e.getMessage(), e);
            return null;
        }
    }


    // ── Filtro de período ─────────────────────────────────────────────────────

    private AfdResponseDTO filtrarPorPeriodo(AfdResponseDTO afd, LocalDate dataInicial, LocalDate dataFinal) {
        if (afd == null) return null;

        List<br.com.atom.chronus_api.dtos.AfdLineDTO> batidasFiltradas = afd.getBatidas()
                .stream()
                .filter(b -> {
                    if (dataFinal == null) return b.getData().equals(dataInicial);
                    return !b.getData().isBefore(dataInicial) && !b.getData().isAfter(dataFinal);
                })
                .toList();

        log.info("Batidas após filtro [{} → {}]: {}",
                dataInicial, dataFinal != null ? dataFinal : "exata", batidasFiltradas.size());

        return new AfdResponseDTO(afd.getTotalLinhas(), batidasFiltradas.size(), batidasFiltradas);
    }


    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String montarBody(String session) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", session);
        return objectMapper.writeValueAsString(body);
    }

    private String montarBody2(String session, LocalDate data) throws IOException {
        Map<String, Object> initialDate = new LinkedHashMap<>();
        initialDate.put("day",   data.getDayOfMonth());
        initialDate.put("month", data.getMonthValue());
        initialDate.put("year",  data.getYear());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session",      session);
        body.put("initial_date", initialDate);
        return objectMapper.writeValueAsString(body);
    }

    private String montarBody3(String session, LocalDate dataInicial, LocalDate dataFinal) throws IOException {
        Map<String, Object> initialDate = new LinkedHashMap<>();
        initialDate.put("day",   dataInicial.getDayOfMonth());
        initialDate.put("month", dataInicial.getMonthValue());
        initialDate.put("year",  dataInicial.getYear());

        Map<String, Object> finalDate = new LinkedHashMap<>();
        finalDate.put("day",   dataFinal.getDayOfMonth());
        finalDate.put("month", dataFinal.getMonthValue());
        finalDate.put("year",  dataFinal.getYear());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session",      session);
        body.put("initial_date", initialDate);
        body.put("final_date",   finalDate);
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