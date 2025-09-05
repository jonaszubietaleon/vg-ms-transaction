package pe.edu.vallegrande.vg_ms_casas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    // Orígenes permitidos estáticos
    private static final List<String> STATIC_ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:4200"
    );

    // Regex para entornos Gitpod
    private static final Pattern GITPOD_REGEX = Pattern.compile(
            "^https://4200-[a-z0-9\\-]+\\.ws-[a-z0-9]+\\.gitpod\\.io$"
    );

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(auth -> auth
                        // Permitir preflight (CORS)
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Swagger sin protección
                        .pathMatchers("/swagger-ui.html", "/v3/api-docs/**", "/swagger-ui/**").permitAll()

                        // Seguridad para /consumption
                        .pathMatchers(HttpMethod.GET, "/consumption/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/consumption/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/consumption/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/consumption/**").hasRole("ADMIN")

                        // Seguridad para /inventories
                        .pathMatchers(HttpMethod.GET, "/inventories/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/inventories/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/inventories/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/inventories/**").hasRole("ADMIN")

                        // Seguridad para /homes
                        .pathMatchers(HttpMethod.GET, "/homes/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/homes/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/homes/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/homes/**").hasRole("ADMIN")

                        // Seguridad para /transactions 🚀
                        .pathMatchers(HttpMethod.GET, "/transactions/**").hasAnyRole("USER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/transactions/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/transactions/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.DELETE, "/transactions/**").hasRole("ADMIN")

                        // Cualquier otra ruta requiere autenticación
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder())
                                .jwtAuthenticationConverter(this::convertJwt)
                        )
                )
                .cors(cors -> cors.configurationSource(dynamicCorsConfigurationSource()))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    private Mono<CustomAuthenticationToken> convertJwt(Jwt jwt) {
        String role = jwt.getClaimAsString("role");

        Collection<GrantedAuthority> authorities = role != null
                ? List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                : List.of();

        return Mono.just(new CustomAuthenticationToken(jwt, authorities));
    }

    private CorsConfigurationSource dynamicCorsConfigurationSource() {
        return new UrlBasedCorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(ServerWebExchange exchange) {
                String origin = exchange.getRequest().getHeaders().getOrigin();

                if (isAllowedOrigin(origin)) {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of(origin));
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    config.setMaxAge(3600L);
                    return config;
                }
                return null;
            }
        };
    }

    private boolean isAllowedOrigin(String origin) {
        if (origin == null) return false;
        return STATIC_ALLOWED_ORIGINS.contains(origin) || GITPOD_REGEX.matcher(origin).matches();
    }
}
