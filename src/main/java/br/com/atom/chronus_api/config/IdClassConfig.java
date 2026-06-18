package br.com.atom.chronus_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class IdClassConfig {

    @Value("${idclass.host}")
    private String host;       // ex: 192.168.1.201

    @Value("${idclass.port:443}")
    private int port;          // ex: 443

    @Value("${idclass.user:admin}")
    private String user;

    @Value("${idclass.password:admin}")
    private String password;

    /** Monta a URL base do relógio */
    public String getBaseUrl() {
        return "https://" + host + ":" + port;
    }
}