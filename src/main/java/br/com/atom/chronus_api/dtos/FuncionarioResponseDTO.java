package br.com.atom.chronus_api.dtos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FuncionarioResponseDTO {

 private long idFuncionario;

 private String nomeFuncionario;

 private String pis;

 private List<AfdLineDTO> batidasPonto;

 private double horasTrabalhadas;

 

}
