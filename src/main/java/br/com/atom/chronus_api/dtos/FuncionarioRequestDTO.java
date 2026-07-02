package br.com.atom.chronus_api.dtos;

import java.time.LocalDate;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FuncionarioRequestDTO {


    private long idFuncionario;

    private String nomeFuncionario;

    private String enderecoFuncionario;

    private String bairroFuncionario;

    private String cidadeFuncionario;

    private String cepFuncionario;

    private String celularFuncionario;

    private String recadoDuncionario;

    private String emailFuncionario;

    private String pis;

    private LocalDate dataAdmissao;

}
