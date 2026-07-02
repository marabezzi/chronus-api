package br.com.atom.chronus_api.dtos;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserDTO {
 
    /** PIS é imutável — serve como chave de identificação do empregado */
    private long    pis;
 
    private String  name;
    private int     code;
    private String  password;
    private boolean admin;
    private long    rfid;
    private String  bars;
    private int     registration;
 
    @JsonProperty("remove_templates")
    private boolean removeTemplates;
 
    private List<Object> templates;
 
}
 