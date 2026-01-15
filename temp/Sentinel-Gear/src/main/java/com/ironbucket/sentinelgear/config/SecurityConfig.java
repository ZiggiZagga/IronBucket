package com.ironbucket.sentinelgear.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveClientRegistrationRepository clientRegistrationRepository() {
        // Create a client registration manually without trying to connect to Keycloak during bootstrap
        ClientRegistration keycloakClient = ClientRegistration.withRegistrationId("keycloak")
            .clientId("dev-client")
            .clientSecret("dev-secret")
            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile")
            .authorizationUri("http://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/auth")
            .tokenUri("http://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/token")
            .userInfoUri("http://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/userinfo")
            .jwkSetUri("http://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/certs")
            .issuerUri("http://steel-hammer-keycloak:7081/realms/dev")
            .build();
        
        return new InMemoryReactiveClientRegistrationRepository(keycloakClient);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(authz -> authz.anyExchange().permitAll())
            .csrf(csrf -> csrf.disable());
        
        return http.build();
    }
}

