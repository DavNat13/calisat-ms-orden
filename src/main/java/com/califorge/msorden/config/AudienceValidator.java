package com.califorge.msorden.config;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Un token solo sirve para este backend si identifica a este backend como su
 * destinatario: evita que un token legitimo emitido para otra API se reutilice
 * aqui (ataque de audience confusion).
 *
 * <p>El claim no siempre se llama igual: Microsoft Entra ID y el ID token de
 * Cognito lo ponen en "aud", pero el ACCESS token de Cognito lo pone en
 * "client_id" (por diseno, para que un access token no se pueda usar donde se
 * espera un ID token). Por eso se aceptan los dos, y se rechaza cualquier token
 * sin audiencia valida.</p>
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> allowedAudiences;

    public AudienceValidator(List<String> allowedAudiences) {
        this.allowedAudiences = allowedAudiences;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = JwtClaims.asStringList(jwt, "aud");
        List<String> clientIds = JwtClaims.asStringList(jwt, "client_id");

        if (audiences.isEmpty() && clientIds.isEmpty()) {
            return rechazar("El token no contiene los claims 'aud' ni 'client_id'.");
        }

        if (!contieneUno(audiences) && !contieneUno(clientIds)) {
            return rechazar("La audiencia del token 'aud=" + audiences
                    + ", client_id=" + clientIds
                    + "' no coincide con la esperada: " + allowedAudiences);
        }

        return OAuth2TokenValidatorResult.success();
    }

    private boolean contieneUno(List<String> valores) {
        return valores.stream().anyMatch(allowedAudiences::contains);
    }

    private OAuth2TokenValidatorResult rechazar(String detalle) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, detalle, null));
    }
}
