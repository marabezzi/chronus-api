package br.com.atom.chronus_api.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.SessionStatus;

import br.com.atom.chronus_api.config.IdClassConfig;
import br.com.atom.chronus_api.dtos.SessionStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gerencia o token de sessão do iDClass.
 *
 * Comportamento:
 *   - Token válido por 480 segundos (8 minutos)
 *   - Renova automaticamente quando faltam 60 segundos para expirar
 *   - Thread-safe — pode ser chamado de múltiplas threads
 *   - Se o token expirar, faz novo login automaticamente
 *
 * Uso:
 *   String session = sessionManager.getSession();
 *   // usa session nas chamadas ao relógio
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManager {

    private final IdClassAuthService authService;
    private final IdClassConfig      idClassConfig;
    private final HttpClient         httpClient;


    /** Token atual de sessão */
    private volatile String        session          = null;

    /** Momento em que o token foi obtido */
    private volatile LocalDateTime sessionObtidoEm = null;

    /** TTL do token em segundos (480 = 8 minutos) */
    private static final int TTL_SEGUNDOS = 480;

    /** Renova quando faltam N segundos para expirar */
    private static final int RENOVAR_ANTES = 60;

    /**
     * Retorna um token de sessão válido.
     *
     * Se não houver sessão, faz login.
     * Se a sessão estiver próxima de expirar, renova.
     * Thread-safe via synchronized.
     *
     * @return token de sessão ou null se não conseguir autenticar
     */
    public synchronized String getSession() {
        // Sem sessão — faz login
        if (session == null || sessionObtidoEm == null) {
            log.info("Sem sessão ativa — fazendo login...");
            return fazerLogin();
        }

        // Verifica tempo restante
        long segundosDecorridos = ChronoUnit.SECONDS.between(
                sessionObtidoEm, LocalDateTime.now());

        long segundosRestantes = TTL_SEGUNDOS - segundosDecorridos;

        // Próximo de expirar — renova
        if (segundosRestantes <= RENOVAR_ANTES) {
            log.info("Sessão expira em {}s — renovando...",
                    segundosRestantes);
            return renovarOuRelogar();
        }

        log.debug("Sessão válida. Expira em {}s.", segundosRestantes);
        return session;
    }

    /**
     * Força o logout da sessão atual e invalida o token.
     */
    public synchronized void invalidar() {
        log.info("Sessão invalidada manualmente.");
        session          = null;
        sessionObtidoEm  = null;
    }

    /**
     * Retorna o status atual da sessão.
     */
    public SessionStatusDTO getStatus() {
    if (session == null || sessionObtidoEm == null) {
        return new SessionStatusDTO(false, null, 0, "Sem sessão ativa");
    }

    long segundosDecorridos = ChronoUnit.SECONDS.between(
            sessionObtidoEm, LocalDateTime.now());
    long segundosRestantes = TTL_SEGUNDOS - segundosDecorridos;

    if (segundosRestantes <= 0) {
        return new SessionStatusDTO(false, null, 0, "Sessão expirada");
    }

    return new SessionStatusDTO(
            true,
            session.substring(0, Math.min(8, session.length())) + "...",
            (int) segundosRestantes,
            "Ativa"
    );
}

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────────────────────────────

    private String fazerLogin() {
        String novaSession = authService.login();
        if (novaSession != null) {
            session         = novaSession;
            sessionObtidoEm = LocalDateTime.now();
        }
        return session;
    }

    /**
     * Tenta renovar a sessão via keep-alive.
     * Se falhar, faz novo login.
     */
    private String renovarOuRelogar() {
        try {
            // Tenta um ping leve para renovar o TTL
            String url = idClassConfig.getBaseUrl()
                    + "/get_info.fcgi?session=" + session;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(
                    req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200
                    && !resp.body().contains("\"error\"")) {
                // Ping ok — renova o timestamp
                sessionObtidoEm = LocalDateTime.now();
                log.info("Sessão renovada via keep-alive.");
                return session;
            }

        } catch (Exception e) {
            log.warn("Keep-alive falhou: {} — fazendo novo login.",
                    e.getMessage());
        }

        // Keep-alive falhou — novo login
        return fazerLogin();
    }
}