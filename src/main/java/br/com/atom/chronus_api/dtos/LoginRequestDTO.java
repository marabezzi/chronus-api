package br.com.atom.chronus_api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de requisição de login ao iDClass.
 *
 * JSON enviado ao relógio:
 * { "login": "admin", "password": "admin" }
 *
 * @Data            → getters, setters, toString, equals, hashCode
 * @NoArgsConstructor → construtor vazio (exigido pelo Jackson)
 * @AllArgsConstructor → construtor com todos os campos (usado no service)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {

    private String login;
    private String password;
}