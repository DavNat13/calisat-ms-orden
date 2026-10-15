package com.califorge.msorden.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proveedores de identidad (OpenID Connect) que el backend acepta como emisores
 * de tokens: Microsoft Entra ID (Azure) y AWS Cognito.
 *
 * <p>Cada proveedor declara su JWK Set (llaves publicas con las que se verifica
 * la firma), los valores admitidos del claim "iss", las audiencias validas y,
 * opcionalmente, el rol por defecto que se asigna cuando el token no trae
 * ninguno.</p>
 *
 * <p>Se lee de application.yaml bajo app.security.jwt.providers</p>
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProviderProperties {

    private List<Provider> providers = new ArrayList<>();

    public List<Provider> getProviders() {
        return providers;
    }

    public void setProviders(List<Provider> providers) {
        this.providers = providers;
    }

    /**
     * Un proveedor de identidad concreto (azure, cognito, ...).
     */
    public static class Provider {

        /** Solo identifica al proveedor en la configuracion y en los logs. */
        private String name;

        /** URL del JWK Set usado para verificar la firma del token. */
        private String jwkSetUri;

        /** Valores admitidos del claim "iss". Si el token trae otro, se rechaza. */
        private List<String> issuers = new ArrayList<>();

        /** Audiencias admitidas ("aud" o "client_id"). Vacio = no se valida. */
        private List<String> audiences = new ArrayList<>();

        /** Rol por defecto (CLIENTE en Cognito). Vacio = no se asigna ninguno. */
        private String defaultRole;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public List<String> getIssuers() {
            return issuers;
        }

        public void setIssuers(List<String> issuers) {
            this.issuers = issuers;
        }

        public List<String> getAudiences() {
            return audiences;
        }

        public void setAudiences(List<String> audiences) {
            this.audiences = audiences;
        }

        public String getDefaultRole() {
            return defaultRole;
        }

        public void setDefaultRole(String defaultRole) {
            this.defaultRole = defaultRole;
        }
    }
}
