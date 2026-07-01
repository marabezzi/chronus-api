package br.com.atom.chronus_api.dtos;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa uma linha parseada do arquivo AFD do iDClass.
 *
 * Layout posicional fixo — Portaria 671/INMETRO:
 *
 * Posição  Tamanho  Conteúdo
 * ──────────────────────────────────────────────────────
 *  0-8       9      NSR  — Número Sequencial de Registro
 *  9-20     12      Data e hora — DDMMAAAAHHmm
 * 21-22      2      Tipo — 01=Entrada 02=Saída (outros=sem tipo)
 * 23-34     12      PIS/CPF do funcionário
 * 35-42      8      CRC/Hash de verificação
 *
 * Linhas especiais (tipo 6) têm layout diferente — são ignoradas.
 * Exemplo de linha real:
 *   000018531 3050320261802 01 295259216 2f410
 *   (sem espaços — mostrado assim só para clareza)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AfdLineDTO {

    /** NSR — identificador único e sequencial da batida no relógio */
    private Long nsr;

    /**
     * Tipo da batida:
     *   1 = Entrada
     *   2 = Saída
     *   3 = Entrada intervalo
     *   4 = Saída intervalo
     *  -1 = Não identificado (linhas especiais)
     */
    private char tipo;

     /** Data da batida */
     private LocalDate data;

    /** Hora da batida */
    private LocalTime hora;

    /** PIS ou CPF do funcionário (12 dígitos com zeros à esquerda) */
    private String pis;

    /** Linha original do AFD para rastreabilidade */
    private String crc;
}
