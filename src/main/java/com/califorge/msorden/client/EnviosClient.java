package com.califorge.msorden.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Cliente HTTP de calisat-ms-envios (fase B): crea el envio al preparar/
 * enviar la orden. Sin service discovery: base URL = CALISAT_ENVIOS_URL
 * (default http://localhost:8086).
 *
 * <p>Resiliencia: si envios esta caido (o la orden ya tiene envio -> 409),
 * la operacion degrada a Optional vacio y la transicion de estado de la
 * orden NO se interrumpe.</p>
 */
@Component
public class EnviosClient {

    private static final Logger log = LoggerFactory.getLogger(EnviosClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EnviosClient(RestTemplate restTemplate,
                        @Value("${CALISAT_ENVIOS_URL:http://localhost:8086}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Crea un envio para la orden indicada.
     *
     * @return el envio creado; vacio si envios no respondio (caido/duplicado)
     */
    public Optional<EnvioCreadoDto> crear(EnvioSolicitudDto solicitud) {
        try {
            EnvioCreadoDto creado = restTemplate.postForObject(
                    baseUrl + "/api/v1/envios", solicitud, EnvioCreadoDto.class);
            return Optional.ofNullable(creado);
        } catch (RestClientException ex) {
            log.warn("Envios no disponible para la orden '{}' ({}): el envio se creara en una fase posterior",
                    solicitud.ordenId(), ex.getMessage());
            return Optional.empty();
        }
    }
}
