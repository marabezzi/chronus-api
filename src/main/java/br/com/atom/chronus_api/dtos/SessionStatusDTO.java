package br.com.atom.chronus_api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta do endpoint GET /api/auth/status.
 *
 * Informa o estado atual da sessão com o relógio sem expor o token.
 *
 * Exemplo de resposta:
 * {
 *   "sessaoAtiva": true,
 *   "segundosRestantes": 342,
 *   "mensagem": "Sessão ativa — expira em 342 segundos"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusDTO {

    private boolean ativa;
    private String  tokenParcial;
    private int     segundosRestantes;
    private String  mensagem;
}