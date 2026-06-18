package br.com.atom.chronus_api.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.cert.X509Certificate;

/**
 * Configura o HttpClient para ignorar SSL autoassinado do iDClass.
 * NÃO usar em outros contextos — apenas para o relógio.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient idClassHttpClient() throws Exception {
        // TrustManager que aceita qualquer certificado
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(
                        X509Certificate[] c, String a) {}
                public void checkServerTrusted(
                        X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new java.security.SecureRandom());

        return HttpClient.newBuilder()
                .sslContext(ctx)
                .build();
    }
}
