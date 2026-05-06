package org.transitclock.config;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
public class Auth2SecurityConfiguration {

    @Value("${transitclock.core.agencyId}")
    private String agencyId;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/key/f78a2e9a/command/agencies").permitAll()
                        .requestMatchers("/api/v1/key/f78a2e9a/agency/" +agencyId+ "/command/getExportFile").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(auth -> auth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://**", "https://**"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*", "Authorization"));
        configuration.setExposedHeaders(List.of("Authorization", "Content-Security-Policy", "Location"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

//    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new JwtGrantedAuthoritiesConverter().convert(jwt);
            var realmAccess = jwt.getClaimAsMap("realm_access");
            var roles = realmAccess != null
                    ? (List<String>) realmAccess.get("roles")
                    : List.<String>of();
            return Stream.concat(authorities.stream(),
                                 roles.stream()
                                         .filter(role -> role.startsWith("ROLE_"))
                                         .map(SimpleGrantedAuthority::new)
                                         .map(GrantedAuthority.class::cast)).toList();
        });
        return converter;
    }
}
