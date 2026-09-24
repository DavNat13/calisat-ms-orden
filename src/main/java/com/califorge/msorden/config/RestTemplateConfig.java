package com.califorge.msorden.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Bean RestTemplate compartido por los clientes inter-servicio (fase B).
 * Sin service discovery: cada cliente fija su base URL por variable de
 * entorno con default localhost (ver paquete client).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
