package br.com.atom.chronus_api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resposta do endpoint GET /api/ponto do Chronus.
 *
 * Retorna os registros parseados do AFD junto com metadados úteis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfdResponseDTO {

    /** Total de linhas recebidas do relógio (incluindo especiais) */
    private int totalLinhas;

    /** Total de batidas válidas parseadas */
    private int totalBatidas;

    /** Lista de batidas parseadas e prontas para uso */
    private List<AfdLineDTO> batidas;
}