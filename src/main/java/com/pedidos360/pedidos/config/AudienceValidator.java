package com.pedidos360.pedidos.config;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validador del claim 'aud' (audience) para tokens JWT emitidos por Microsoft Entra ID.
 *
 * Spring Security valida por defecto firma, vigencia (exp/nbf) e issuer,
 * pero no valida la audiencia especifica. Sin este validador, un token emitido
 * por el mismo tenant para otra API seria aceptado. Esta clase implementa
 * defensa en profundidad validando que el token este destinado a esta API.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> audiencesValidas;

    public AudienceValidator(List<String> audiencesValidas) {
        this.audiencesValidas = audiencesValidas;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> tokenAudiences = jwt.getAudience();

        if (tokenAudiences != null) {
            for (String audience : tokenAudiences) {
                if (this.audiencesValidas.contains(audience)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }

        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "El token no contiene una audiencia valida para esta API. Audiencias esperadas: " + this.audiencesValidas,
                null
        );

        return OAuth2TokenValidatorResult.failure(error);
    }
}
