package com.ironbucket.graphiteforge.config;

import com.ironbucket.graphiteforge.filter.RequestCorrelationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
        ServerHttpSecurity http,
        RequestCorrelationFilter requestCorrelationFilter
    ) {
        http
            .addFilterAt(requestCorrelationFilter, SecurityWebFiltersOrder.FIRST)
            .authorizeExchange(authz -> authz
                .pathMatchers("/actuator/**").permitAll()
                .anyExchange().authenticated())
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
