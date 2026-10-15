package com.califorge.msorden.config;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lectura defensiva de claims del JWT.
 *
 * <p>Nimbus/Spring exponen un mismo claim como String o como lista de String
 * segun el proveedor ("aud" llega como lista, "client_id" como cadena suelta).
 * En vez de depender de la conversion implicita de Spring (que puede lanzar
 * IllegalArgumentException si el tipo no encaja), se normaliza aqui a una lista
 * de strings.</p>
 */
final class JwtClaims {

    private JwtClaims() {
    }

    /**
     * Devuelve el claim como lista de strings; una cadena suelta se devuelve como
     * lista de un elemento y el claim ausente o nulo como lista vacia.
     */
    static List<String> asStringList(Jwt jwt, String claim) {
        Object valor = jwt.getClaim(claim);

        if (valor == null) {
            return List.of();
        }
        if (valor instanceof List<?> lista) {
            return lista.stream()
                    .filter(elemento -> elemento != null)
                    .map(String::valueOf)
                    .toList();
        }
        return List.of(String.valueOf(valor));
    }
}
