package com.califorge.msorden.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Bean RestTemplate compartido por los clientes inter-servicio.
 * Lleva ServiceTokenInterceptor en TODAS las peticiones salientes, de modo
 * que el receptor puede identificar la llamada como de servicio (ROLE_SERVICIO)
 * sin intercambiar JWT de usuario.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(ServiceTokenInterceptor serviceTokenInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(List.of(serviceTokenInterceptor));
        return restTemplate;
    }
}
