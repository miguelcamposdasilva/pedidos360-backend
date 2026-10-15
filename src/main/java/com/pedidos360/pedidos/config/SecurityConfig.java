package com.pedidos360.pedidos.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracion de seguridad de Spring Security con OAuth 2.0 / JWT.
 *
 * Valida firma, vigencia, issuer y audience de los tokens de Microsoft Entra ID.
 * Convierte los roles de Entra ID a GrantedAuthorities de Spring y declara las reglas
 * de autorizacion de forma centralizada sin anotaciones dispersas.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final List<String> audiences;
    private final String allowedOrigins;

    public SecurityConfig(
            @Value("${entra.issuer-uri}") String issuerUri,
            @Value("${entra.audiences}") String audiences,
            @Value("${cors.allowed-origins:}") String allowedOrigins) {

        this.issuerUri = issuerUri;
        this.audiences = Arrays.stream(audiences.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins.trim() : "";
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints publicos de diagnostico, salud y preflight CORS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/publico/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/**").permitAll()

                // Endpoints de administracion de RabbitMQ (accesibles para pruebas y evaluacion)
                .requestMatchers("/admin/**", "/queues/**", "/exchanges/**", "/bindings/**").permitAll()

                // Endpoints protegidos de pedidos autorizados por roles de Entra ID
                .requestMatchers(HttpMethod.GET, "/api/pedidos/**").hasAnyRole("Pedidos.Lector", "Pedidos.Admin")
                .requestMatchers(HttpMethod.POST, "/api/pedidos/**").hasRole("Pedidos.Admin")
                .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**").hasRole("Pedidos.Admin")

                // Endpoint de identidad /api/me: requiere cualquier token valido
                .requestMatchers("/api/me").authenticated()

                // Cualquier otra peticion requiere autenticacion
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(this.jwtDecoder())
                    .jwtAuthenticationConverter(this.jwtAuthenticationConverter())
                )
            );

        // Si se especificaron origenes CORS (desarrollo local), se configura el filtro CORS
        if (!this.allowedOrigins.isEmpty()) {
            http.cors(cors -> cors.configurationSource(this.corsConfigurationSource()));
        } else {
            // En AWS, las cabeceras CORS las maneja el API Gateway
            http.cors(cors -> cors.disable());
        }

        return http.build();
    }

    /**
     * Construye el decodificador JWT de Nimbus validando firma, issuer, vigencia y audience.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromIssuerLocation(this.issuerUri);

        OAuth2TokenValidator<Jwt> validadorPorDefecto = JwtValidators.createDefaultWithIssuer(this.issuerUri);
        OAuth2TokenValidator<Jwt> validadorAudience = new AudienceValidator(this.audiences);

        OAuth2TokenValidator<Jwt> validadorCompleto =
                new DelegatingOAuth2TokenValidator<>(validadorPorDefecto, validadorAudience);

        jwtDecoder.setJwtValidator(validadorCompleto);

        return jwtDecoder;
    }

    /**
     * Traduce los claims de Microsoft Entra ID a autoridades de Spring Security:
     * - claim "roles" -> ROLE_Pedidos.Admin, ROLE_Pedidos.Lector
     * - claim "scp"   -> SCOPE_access_as_user
     */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // Extrae los roles de aplicacion de Entra ID
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                for (String role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }

            // Extrae los permisos delegados (scopes)
            String scp = jwt.getClaimAsString("scp");
            if (scp != null && !scp.isBlank()) {
                for (String scope : scp.split(" ")) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
                }
            }

            return authorities;
        });

        return converter;
    }

    /**
     * Configuracion CORS para entorno local con Angular (http://localhost:4200).
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = Arrays.stream(this.allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "PUT", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
