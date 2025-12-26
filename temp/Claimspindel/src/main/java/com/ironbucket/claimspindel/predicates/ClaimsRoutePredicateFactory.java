package com.ironbucket.claimspindel.predicates;

import java.text.ParseException;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.cloud.gateway.handler.predicate.AbstractRoutePredicateFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nimbusds.jwt.SignedJWT;

/**
 * Usage in application.yml:
 * predicates:
 *   - Claims=region,east
 */
@Component
public class ClaimsRoutePredicateFactory extends AbstractRoutePredicateFactory<ClaimsRoutePredicateFactory.Config> {

    public ClaimsRoutePredicateFactory() {
        super(Config.class);
    }

    @Override
    public Predicate<ServerWebExchange> apply(Config config) {
        return exchange -> {
        	 String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        	
             if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                 return false;
             }

             String token = authHeader.substring(7);
             try {
            	 SignedJWT jwt = SignedJWT.parse(token);
            	 String claimName=config.claimName();
            	 if("role".equals(claimName)) {
                     JsonNode claims = new ObjectMapper().valueToTree(jwt.getJWTClaimsSet().getClaims());
                     ArrayNode roles = ((ArrayNode)claims.get("realm_access").get("roles"));
                     boolean hasRole = false;
                     for(JsonNode checkRoleNode: roles) {
                    	 hasRole = checkRoleNode.asText().equals(config.expectedValue);
                    	 if(hasRole) {
                    		 break;
                    	 }
                     }
                     return hasRole;
            	 }else {
            		 return false;
            	 }
             } catch (ParseException e) {
                 e.printStackTrace();
                 return false;
             }
        	 
        	 
           
        };
    }
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("claimName", "expectedValue");
    }
    record Config (String claimName,  String expectedValue) {}
}
