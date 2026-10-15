package com.califorge.msorden.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Construye un NimbusJwtDecoder POR proveedor de identidad (Azure y Cognito) y
 * los agrupa por issuer dentro de un MultiIssuerJwtDecoder, de modo que un unico
 * resource server valida tokens de ambos emisores y ninguno acepta los del otro.
 *
 * <p>Cada decoder valida: firma (contra su propio JWK Set), fechas (exp/nbf),
 * que el "iss" pertenezca al proveedor y que la audiencia sea la suya.</p>
 */
@Configuration
@EnableConfigurationProperties(JwtProviderProperties.class)
public class JwtDecoderConfig {

    /**
     * Un decoder por emisor y un decorador que enruta cada token al decoder de su
     * proveedor. Si no hay proveedores configurados se falla al arrancar: un
     * resource server que no valida nada es un agujero de seguridad.
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProviderProperties properties) {
        Map<String, JwtDecoder> decodersPorEmisor = new LinkedHashMap<>();

        for (JwtProviderProperties.Provider provider : properties.getProviders()) {
            NimbusJwtDecoder decoder = decoderDeProveedor(provider);
            for (String issuer : provider.getIssuers()) {
                decodersPorEmisor.put(issuer, decoder);
            }
        }

        if (decodersPorEmisor.isEmpty()) {
            throw new IllegalStateException(
                    "No hay proveedores JWT en app.security.jwt.providers: "
                            + "ningun issuer queda registrado.");
        }
        return new MultiIssuerJwtDecoder(decodersPorEmisor);
    }

    private NimbusJwtDecoder decoderDeProveedor(JwtProviderProperties.Provider provider) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(provider.getJwkSetUri())
                .build();

        List<OAuth2TokenValidator<Jwt>> validadores = new ArrayList<>();
        validadores.add(emisorAdmitido(provider.getIssuers()));
        // Sin audiencias declaradas no se valida "aud" (comportamiento previo).
        if (!provider.getAudiences().isEmpty()) {
            validadores.add(new AudienceValidator(provider.getAudiences()));
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validadores));
        return decoder;
    }

    /**
     * Valida fechas (exp/nbf) y que el "iss" del token coincida con ALGUNO de los
     * issuers declarados para el proveedor.
     *
     * <p>Se construye un validador con JwtValidators.createDefaultWithIssuer por
     * issuer y se acepta si pasa cualquiera (semantica any-of), porque
     * DelegatingOAuth2TokenValidator exige que pasen TODOS y un proveedor con
     * varios issuers admitidos nunca cumpliria eso.</p>
     */
    private OAuth2TokenValidator<Jwt> emisorAdmitido(List<String> issuers) {
        List<OAuth2TokenValidator<Jwt>> porIssuer = new ArrayList<>();
        for (String issuer : issuers) {
            porIssuer.add(JwtValidators.createDefaultWithIssuer(issuer));
        }

        if (porIssuer.isEmpty()) {
            return JwtValidators.createDefault();
        }

        return jwt -> {
            for (OAuth2TokenValidator<Jwt> validador : porIssuer) {
                if (!validador.validate(jwt).hasErrors()) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token",
                    "El issuer del token no esta admitido para el proveedor. iss: "
                            + jwt.getClaimAsString("iss"),
                    null));
        };
    }
}
