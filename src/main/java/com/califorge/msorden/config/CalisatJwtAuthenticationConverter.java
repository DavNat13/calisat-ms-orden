package com.califorge.msorden.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * REGLA DE NEGOCIO: convierte los claims de rol del token en autoridades Spring
 * Security con prefijo "ROLE_" y en mayusculas, para poder usar hasAnyRole(...).
 *
 * <ul>
 *   <li><b>Microsoft Entra ID</b> publica los roles en el claim "roles": se
 *       extraen tal cual (igual que antes de la autenticacion dual).</li>
 *   <li><b>AWS Cognito</b> no usa "roles": agrupa en "cognito:groups" (solo
 *       presente en el ID token, no en el access token que llega aqui). Por eso
 *       TODO token de Cognito recibe ademas su rol por defecto declarado en
 *       application.yaml (CLIENTE): sin este fallback un usuario de Cognito
 *       quedaria sin autoridades y la API le devolveria 403.</li>
 * </ul>
 *
 * <p>El emisor se identifica por el claim "iss", que MultiIssuerJwtDecoder ya se
 * aseguro de que pertenece a un proveedor registrado.</p>
 */
@Component
public class CalisatJwtAuthenticationConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String PREFIJO_ROL = "ROLE_";

    private final JwtProviderProperties properties;

    public CalisatJwtAuthenticationConverter(JwtProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> autoridades = new LinkedHashSet<>();

        // Microsoft Entra ID -> claim "roles"; AWS Cognito -> "cognito:groups".
        autoridades.addAll(desdeClaim(jwt, "roles"));
        autoridades.addAll(desdeClaim(jwt, "cognito:groups"));

        // Rol por defecto del emisor (CLIENTE en Cognito): obligatorio, garantiza
        // que ningun usuario de Cognito quede sin rol aunque no venga groups.
        JwtProviderProperties.Provider emisor = proveedorDe(jwt.getClaimAsString("iss"));
        if (emisor != null && emisor.getDefaultRole() != null
                && !emisor.getDefaultRole().isBlank()) {
            autoridades.add(autoridad(emisor.getDefaultRole()));
        }

        return autoridades;
    }

    private Collection<GrantedAuthority> desdeClaim(Jwt jwt, String claim) {
        return JwtClaims.asStringList(jwt, claim).stream()
                .filter(rol -> rol != null && !rol.isBlank())
                .map(this::autoridad)
                .toList();
    }

    private GrantedAuthority autoridad(String rol) {
        return new SimpleGrantedAuthority(PREFIJO_ROL + rol.trim().toUpperCase(Locale.ROOT));
    }

    private JwtProviderProperties.Provider proveedorDe(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        return properties.getProviders().stream()
                .filter(proveedor -> proveedor.getIssuers().contains(issuer))
                .findFirst()
                .orElse(null);
    }
}
