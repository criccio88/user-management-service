package it.intesigroup.ums.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${security.enabled:true}")
    private boolean securityEnabled;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        http.authorizeHttpRequests(auth -> {
            if (securityEnabled) {
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/users/**")
                        .hasAnyRole("OWNER", "OPERATOR", "MAINTAINER", "DEVELOPER", "REPORTER");
                auth.requestMatchers(HttpMethod.POST, "/api/users/**")
                        .hasAnyRole("OWNER", "MAINTAINER");
                auth.requestMatchers(HttpMethod.PUT, "/api/users/**")
                        .hasAnyRole("OWNER", "MAINTAINER");
                auth.requestMatchers(HttpMethod.DELETE, "/api/users/**")
                        .hasAnyRole("OWNER", "MAINTAINER");
                auth.requestMatchers(HttpMethod.POST, "/api/users/*/disable")
                        .hasAnyRole("OWNER", "MAINTAINER");
                auth.anyRequest().authenticated();
            } else {
                auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/**").permitAll();
                auth.requestMatchers("/api/**").permitAll();
                auth.anyRequest().permitAll();
            }
        });
        if (securityEnabled) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object ra = jwt.getClaims().get("realm_access");
        if (ra instanceof Map<?, ?> map) {
            Object roles = map.get("roles");
            if (roles instanceof List<?> list) {
                return list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
