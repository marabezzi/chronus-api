package br.com.atom.chronus_api.service;

import br.com.atom.chronus_api.dtos.AfdLineDTO;
import br.com.atom.chronus_api.dtos.AfdResponseDTO;
import br.com.atom.chronus_api.dtos.FuncionarioResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class AfdParser {

    /*
     * Portaria 671/INMETRO — layout posicional:
     *
     *   Posição  Tamanho  Conteúdo
     *   ───────────────────────────────────────────────
     *   0-8       9       NSR
     *   9-20     12       Data+hora — DDMMAAAAHHmm
     *   21-22     2       Tipo — 01=Entrada  02=Saída
     *   23-34    12       PIS (12 dígitos com zeros)
     *   35-42     8       CRC
     *   ───────────────────────────────────────────────
     *   Total = 43 chars  (TAMANHO_BATIDA)
     *
     * Empregado tipo 5 (Portaria 1510):
     *   NSR(9) + tipo_afd(1) + data(8) + hora(4) + op(1) + PIS(12) + nome(52) + CRC(4) = 91 chars
     */

    private static final int TAMANHO_BATIDA    = 43;
    private static final int TAMANHO_EMPREGADO = 91;

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("ddMMyyyyHHmm");


    // ── 1. Retorna AfdResponseDTO — usado pelo BatidasLogService ─────────────

    public AfdResponseDTO parseBatidas(String body) {
        String[] linhas = body.split("\\r?\\n");
        List<AfdLineDTO> batidas = new ArrayList<>();

        for (String linha : linhas) {
            linha = linha.stripTrailing();
            if (linha.length() == TAMANHO_BATIDA) {
                AfdLineDTO b = parseLinhaBatida(linha);
                if (b != null) batidas.add(b);
            }
        }

        log.info("AFD: {} linhas | {} batidas parseadas", linhas.length, batidas.size());
        return new AfdResponseDTO(linhas.length, batidas.size(), batidas);
    }


    // ── 2. Retorna List<FuncionarioResponseDTO> — usado pelo FuncionariosService

    public List<FuncionarioResponseDTO> parseFuncionarios(String body) {
        Map<String, String>           nomes      = new LinkedHashMap<>();
        Map<String, List<AfdLineDTO>> batidasMap = new LinkedHashMap<>();

        for (String rawLine : body.split("\\r?\\n")) {
            String linha = rawLine.stripTrailing();

            if (linha.length() == TAMANHO_BATIDA) {
                AfdLineDTO b = parseLinhaBatida(linha);
                if (b != null) {
                    batidasMap
                        .computeIfAbsent(b.getPis(), k -> new ArrayList<>())
                        .add(b);
                }

            } else if (linha.length() == TAMANHO_EMPREGADO) {
                parseEmpregado(linha, nomes);
            }
        }

        List<FuncionarioResponseDTO> resultado = new ArrayList<>();
        long id = 1;

        for (Map.Entry<String, String> entry : nomes.entrySet()) {
            String pis    = entry.getKey();
            List<AfdLineDTO> batidas = batidasMap.getOrDefault(pis, Collections.emptyList());

            FuncionarioResponseDTO dto = new FuncionarioResponseDTO();
            dto.setIdFuncionario(id++);
            dto.setPis(pis);
            dto.setNomeFuncionario(entry.getValue());
            dto.setBatidasPonto(batidas);
            dto.setHorasTrabalhadas(calcularHoras(batidas));
            resultado.add(dto);
        }

        log.info("AFD: {} funcionário(s) parseado(s)", resultado.size());
        return resultado;
    }


    // ── Parseia linha de batida (43 chars) ────────────────────────────────────
    //
    //   [0-8]   NSR          (9 chars)
    //   [9-20]  DDMMAAAAHHmm (12 chars)
    //   [21-22] Tipo         (2 chars)  01=Entrada  02=Saída
    //   [23-34] PIS          (12 chars)
    //   [35-42] CRC          (8 chars)

    private AfdLineDTO parseLinhaBatida(String linha) {
        try {
            String nsrStr = linha.substring(0, 9).strip();
            if (nsrStr.isBlank() || !nsrStr.chars().allMatch(Character::isDigit)) {
                log.debug("NSR inválido: [{}]", linha);
                return null;
            }

            long nsr = Long.parseLong(nsrStr);

            String    dtStr = linha.substring(9, 21);
            LocalDate data  = LocalDate.parse(dtStr, FMT_DATETIME);
            LocalTime hora  = LocalTime.parse(dtStr, FMT_DATETIME);

            String tipoStr = linha.substring(21, 23);
            char   tipo    = tipoParaChar(tipoStr);

            String pis = linha.substring(23, 35);
            String crc = linha.substring(35, 43);

            return new AfdLineDTO(nsr, tipo, data, hora, pis, crc);

        } catch (Exception e) {
            log.warn("Linha inválida ignorada [{}]: {}", linha, e.getMessage());
            return null;
        }
    }


    // ── Parseia empregado (91 chars) ──────────────────────────────────────────
    //
    //   [0-8]   NSR     (9)
    //   [9]     tipo_afd(1)  — '5'
    //   [10-17] data    (8)  DDMMYYYY
    //   [18-21] hora    (4)  HHmm
    //   [22]    op      (1)  I=Inclusão  A=Alteração  E=Exclusão
    //   [23-34] PIS     (12)
    //   [35-86] nome    (52)
    //   [87-90] CRC     (4)

    private void parseEmpregado(String linha, Map<String, String> nomes) {
        char op = linha.charAt(22);
        if (op != 'I' && op != 'A') return;

        String pis  = linha.substring(23, 35);
        String nome = linha.substring(35, 87).trim();
        nomes.put(pis, nome);
        log.debug("Empregado: PIS={} nome={}", pis, nome);
    }


    // ── Converte string "01"/"02" para char '1'/'2' ───────────────────────────

    private char tipoParaChar(String tipoStr) {
        return switch (tipoStr.strip()) {
            case "01" -> '1'; // Entrada
            case "02" -> '2'; // Saída
            case "03" -> '3'; // Entrada intervalo
            case "04" -> '4'; // Saída intervalo
            default   -> (char) -1; // Não identificado
        };
    }


    // ── Calcula horas trabalhadas (soma pares entrada/saída) ──────────────────

    private double calcularHoras(List<AfdLineDTO> batidas) {
        List<AfdLineDTO> ordenadas = batidas.stream()
                .sorted(Comparator
                        .comparing((AfdLineDTO b) -> b.getData())   // lambda evita null-safety warning
                        .thenComparing((AfdLineDTO b) -> b.getHora()))
                .toList();

        double totalMinutos = 0;
        for (int i = 0; i + 1 < ordenadas.size(); i += 2) {
            LocalTime entrada = ordenadas.get(i).getHora();
            LocalTime saida   = ordenadas.get(i + 1).getHora();
            if (saida.isAfter(entrada)) {
                totalMinutos += Duration.between(entrada, saida).toMinutes();
            }
        }
        return Math.round((totalMinutos / 60.0) * 100.0) / 100.0;
    }
}