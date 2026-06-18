package br.com.atom.chronus_api.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionStatusDTO {
    private boolean ativa;
    private String  tokenParcial;
    private int     segundosRestantes;
    private String  mensagem;
}