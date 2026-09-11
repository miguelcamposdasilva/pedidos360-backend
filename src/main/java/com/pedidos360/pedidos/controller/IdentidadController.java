package com.pedidos360.pedidos.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de apoyo para la demostracion en vivo: permiten mostrar
 * en pantalla los claims que el microservicio esta leyendo del token.
 */
@RestController
@RequestMapping("/api")
public class IdentidadController {

    @GetMapping("/publico/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "servicio", "pedidos-service",
                "mensaje", "Endpoint publico: no requiere token");
    }

    @GetMapping("/me")
    public Map<String, Object> yo(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication) {

        List<String> autoridades = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Map.of(
                "sub", jwt.getSubject(),
                "nombre", String.valueOf(jwt.getClaimAsString("name")),
                "usuario", String.valueOf(jwt.getClaimAsString("preferred_username")),
                "tenant", String.valueOf(jwt.getClaimAsString("tid")),
                "issuer", String.valueOf(jwt.getIssuer()),
                "audience", jwt.getAudience(),
                "roles", jwt.getClaimAsStringList("roles") == null
                        ? List.of()
                        : jwt.getClaimAsStringList("roles"),
                "scopes", String.valueOf(jwt.getClaimAsString("scp")),
                "expira", String.valueOf(jwt.getExpiresAt()),
                "autoridadesSpring", autoridades);
    }
}
