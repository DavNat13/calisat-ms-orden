package com.califorge.msorden.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Anade la cabecera X-Service-Token a todas las peticiones salientes de este
 * microservicio. Es el unico credencial MS->MS: el receptor lo valida en
 * ServiceTokenFilter y lo convierte en ROLE_SERVICIO (no lleva JWT porque
 * no hay usuario detras de una llamada servidor a servidor).
 */
@Component
public class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Service-Token";

    private final String token;

    public ServiceTokenInterceptor(@Value("${calisat.servicio.token:}") String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        if (token != null && !token.isBlank()) {
            request.getHeaders().set(HEADER, token);
        }
        return execution.execute(request, body);
    }
}
