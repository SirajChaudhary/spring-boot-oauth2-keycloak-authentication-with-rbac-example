package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // allow swagger/openapi and actuator if needed
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").permitAll()
                        // everything else requires authentication
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    /**
     * Convert Keycloak realm roles (realm_access.roles) to Spring authorities prefixed with "ROLE_"
     */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Keycloak stores roles under "realm_access.roles" (default). Configure it:
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        // default claim name is "scope", but we want realm_access.roles - we'll use a custom converter below
        // However JwtGrantedAuthoritiesConverter only reads from "scope" or "scp". So we need a small wrapper:
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Use JwtGrantedAuthoritiesConverter for scopes (if any)
            var authorities = grantedAuthoritiesConverter.convert(jwt);

            // Extract realm roles
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null && realmAccess.containsKey("roles")) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof java.util.Collection) {
                    ((java.util.Collection<?>) rolesObj).forEach(r -> {
                        if (r != null) {
                            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r.toString()));
                        }
                    });
                }
            }

            return authorities;
        });

        return converter;
    }
}
