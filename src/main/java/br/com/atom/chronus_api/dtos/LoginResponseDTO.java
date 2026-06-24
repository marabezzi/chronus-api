package br.com.atom.chronus_api.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO da resposta de login do iDClass.
 *
 * JSON retornado pelo relógio:
 * { "session": "abc123xyz..." }
 *
 * @Data                → getters, setters, toString, equals, hashCode
 * @NoArgsConstructor   → construtor vazio exigido pelo Jackson para desserializar
 * @JsonIgnoreProperties → ignora campos extras que o relógio possa retornar
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponseDTO {

    // Token de sessão — usado como cookie nos próximos requests:
    // Cookie: session=abc123xyz...
    private String session;
}