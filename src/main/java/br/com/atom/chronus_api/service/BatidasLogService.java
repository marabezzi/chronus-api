package br.com.atom.chronus_api.service;

import br.com.atom.chronus_api.config.IdClassConfig;
import br.com.atom.chronus_api.dtos.AfdLineDTO;
import br.com.atom.chronus_api.dtos.AfdResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Busca e parseia o AFD (Arquivo Fonte de Dados) do iDClass.
 *
 * Endpoint : POST /get_afd.fcgi
 * Body     : { "session": "<token>" }
 * Resposta : texto fixo no formato AFD (Portaria 1510/671)
 *
 * Tipos de registro iDClass:
 *  Tipo 2 → Empregador
 *  Tipo 3 → Marcação de ponto (batida)  ← parseamos este
 *  Tipo 4 → Cabeçalho do equipamento
 *  Tipo 5 → Empregado (I/A/E) ou batida ajustada (38 chars)
 *  Tipo 6 → Evento do relógio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatidasLogService {

    private final IdClassConfig    config;
    private final HttpClient       httpClient;
    private final SessionManager   sessionManager;
    private final IdClassAuthService authService;
    private final ObjectMapper     objectMapper;

    // Formato: NSR(9) + tipo(1) + data(8) + hora(4) + PIS(12) + CRC(4) = 38 chars
    private static final int TAMANHO_BATIDA = 38;

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HHmm");

    // ── Ponto de entrada ─────────────────────────────────────────────────────

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
                return parseAfd(response.body());
            }

            // Sessão expirou — renova e retenta uma vez
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
                    return parseAfd(retry.body());
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

    // ── Parser AFD ───────────────────────────────────────────────────────────

    private AfdResponseDTO parseAfd(String body) {
        String[] linhas = body.split("\\r?\\n");
        List<AfdLineDTO> batidas = new ArrayList<>();

        for (String linha : linhas) {
            linha = linha.stripTrailing();
            if (linha.length() < 10) continue;

            char tipo = linha.charAt(9);

            // Tipo 3 = batida normal
            // Tipo 5 com 38 chars e dígito na pos 22 = batida ajustada
            boolean isBatida = (tipo == '3' && linha.length() == TAMANHO_BATIDA)
                    || (tipo == '5' && linha.length() == TAMANHO_BATIDA
                            && Character.isDigit(linha.charAt(22)));

            if (isBatida) {
                AfdLineDTO batida = parseBatida(linha);
                if (batida != null) {
                    batidas.add(batida);
                }
            }
        }

        log.info("AFD: {} linhas totais | {} batidas parseadas", linhas.length, batidas.size());
        return new AfdResponseDTO(linhas.length, batidas.size(), batidas);
    }

    /**
     * Parseia uma linha de batida (tipo 3 ou tipo 5 ajustada):
     *   [0-8]  NSR       (9 chars)
     *   [9]    Tipo      (1 char)
     *   [10-17] Data     DDMMYYYY (8 chars)
     *   [18-21] Hora     HHMM     (4 chars)
     *   [22-33] PIS/Crachá        (12 chars)
     *   [34-37] CRC               (4 chars)
     */
    private AfdLineDTO parseBatida(String linha) {
        try {
            // NSR — posições [0, 9)  → 9 chars
            String nsrStr = linha.substring(0, 9).strip();
            if (nsrStr.isBlank() || !nsrStr.chars().allMatch(Character::isDigit)) {
                log.debug("NSR inválido na linha [{}]", linha);
                return null;
            }
            long nsr = Long.parseLong(nsrStr);
    
            // Tipo — posição 9, 1 char (manter como char, sem converter para int)
            char tipo = linha.charAt(9);
    
            // Data — posições [10, 18)  → 8 chars  DDMMYYYY
            String dataStr = linha.substring(10, 18).strip();
            if (dataStr.length() != 8 || !dataStr.chars().allMatch(Character::isDigit)) {
                log.debug("Data inválida [{}] na linha [{}]", dataStr, linha);
                return null;
            }
    
            // Hora — posições [18, 22)  → 4 chars  HHMM
            String horaStr = linha.substring(18, 22).strip();
            if (horaStr.length() != 4 || !horaStr.chars().allMatch(Character::isDigit)) {
                log.debug("Hora inválida [{}] na linha [{}]", horaStr, linha);
                return null;
            }
    
            // PIS — posições [22, 34)  → 12 chars
            String pis = linha.substring(22, 34).strip();
    
            // CRC — posições [34, 38)  → 4 chars
            String crc = linha.substring(34, 38);
    
            LocalDate data = LocalDate.parse(dataStr, FMT_DATA);
            LocalTime hora = LocalTime.parse(horaStr, FMT_HORA);
    
            return new AfdLineDTO(nsr, tipo, data, hora, pis, crc);
    
        } catch (Exception e) {
            String charCodes = linha.length() >= 10
                    ? String.format("chars[0]=%d chars[9]=%d", (int) linha.charAt(0), (int) linha.charAt(9))
                    : "linha curta";
            log.warn("Falha ao parsear linha AFD [{}] {} → {}", linha, charCodes, e.getMessage());
            return null;
        }
    }
    // ── HTTP ─────────────────────────────────────────────────────────────────

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


    //---------------- Pesquisa Batidas por data

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
                return filtrarPorPeriodo(parseAfd(response.body()), dataInicial, dataFinal);
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
                    return filtrarPorPeriodo(parseAfd(retry.body()), dataInicial, dataFinal);
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
        body.put("session",       session);
        body.put("initial_date",  initialDate);
        body.put("final_date",    finalDate);
    
        return objectMapper.writeValueAsString(body);
    }
    
    private AfdResponseDTO filtrarPorPeriodo(AfdResponseDTO afd, LocalDate dataInicial, LocalDate dataFinal) {
        if (afd == null) return null;
    
        List<AfdLineDTO> batidasFiltradas = afd.getBatidas()
                .stream()
                .filter(b -> {
                    if (dataFinal == null) {
                        return b.getData().equals(dataInicial);           // data exata
                    }
                    return !b.getData().isBefore(dataInicial)
                        && !b.getData().isAfter(dataFinal);               // período
                })
                .toList();
    
        log.info("Batidas após filtro [{} → {}]: {}",
                dataInicial, dataFinal != null ? dataFinal : "exata", batidasFiltradas.size());
    
        return new AfdResponseDTO(afd.getTotalLinhas(), batidasFiltradas.size(), batidasFiltradas);
    }
}