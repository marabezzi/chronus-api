package br.com.atom.chronus_api.dtos;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDTO {
 
    private String  name;
    private long    pis;
    private int     code;
 
    @JsonProperty("templates_count")
    private int     templatesCount;
 
    private String  password;
    private boolean admin;
    private long    rfid;
    private String  bars;
    private int     registration;
 
}
 