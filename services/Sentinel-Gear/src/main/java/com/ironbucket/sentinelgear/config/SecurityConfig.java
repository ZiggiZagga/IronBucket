package com.ironbucket.sentinelgear.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
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

    private static final String KEYCLOAK_DEFAULT = "https://steel-hammer-keycloak:7081";

    @Bean
    @ConditionalOnMissingBean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(Environment environment) {
        String keycloakUrl = environment.getProperty("KEYCLOAK_URL", KEYCLOAK_DEFAULT);
        String realmBase = keycloakUrl + "/realms/dev";
        // Create a client registration manually without trying to connect to Keycloak during bootstrap
        ClientRegistration keycloakClient = ClientRegistration.withRegistrationId("keycloak")
            .clientId("dev-client")
            .clientSecret("dev-secret")
            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile")
            .authorizationUri(realmBase + "/protocol/openid-connect/auth")
            .tokenUri(realmBase + "/protocol/openid-connect/token")
            .userInfoUri(realmBase + "/protocol/openid-connect/userinfo")
            .jwkSetUri(realmBase + "/protocol/openid-connect/certs")
            .issuerUri(realmBase)
            .build();
        
        return new InMemoryReactiveClientRegistrationRepository(keycloakClient);
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .authorizeExchange(authz -> authz
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers("/graphql", "/graphql/**").permitAll()
                .anyExchange().authenticated())
            .csrf(csrf -> csrf.disable())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        
        return http.build();
    }
}

