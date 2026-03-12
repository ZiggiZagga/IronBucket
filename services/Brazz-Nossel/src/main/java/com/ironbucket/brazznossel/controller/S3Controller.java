package com.ironbucket.brazznossel.controller;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import com.ironbucket.brazznossel.service.S3ProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/s3")
public class S3Controller {
	
	private final S3ProxyService s3ProxyService;
	
	// Constructor injection for testability
	public S3Controller(S3ProxyService s3ProxyService) {
		this.s3ProxyService = s3ProxyService;
	}
	
	// No-arg constructor for tests
	public S3Controller() {
		this.s3ProxyService = null;
	}
	
	/**
	 * Convert JWT to NormalizedIdentity
	 */
	private NormalizedIdentity extractIdentity(Jwt jwt) {
		String username = jwt.getClaimAsString("preferred_username");
		if (username == null) {
			username = jwt.getSubject();
		}
		
		String tenant = jwt.getClaimAsString("tenant");
		if (tenant == null) {
			tenant = "default";
		}
		
		List<String> roles = jwt.getClaimAsStringList("roles");
		if (roles == null) {
			roles = Collections.emptyList();
		}
		
		return NormalizedIdentity.builder()
				.userId(jwt.getSubject())
				.tenantId(tenant)
				.preferredUsername(username)
				.email(jwt.getClaimAsString("email"))
				.roles(roles)
				.region(jwt.getClaimAsString("region"))
				.build();
	}
	
	@GetMapping(path="/dev")
	public Mono<String> helloDev() {	
		return Mono.just("Hello brazznossel dev user");
	}
	
	@GetMapping(path="/admin")
	public Mono<String> helloAdmin() {	
		return Mono.just("Hello brazznossel admin user");
	}
	
	/**
	 * List buckets for the authenticated user's tenant
	 */
	@GetMapping(path="/buckets")
	public Mono<String> listBuckets(@AuthenticationPrincipal Jwt principal) {
		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}
		
		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.listBuckets(identity);
	}
	
	/**
	 * Get object from S3
	 */
	@GetMapping(path="/object/{bucket}/{key}")
	public Mono<byte[]> getObject(
			@PathVariable String bucket,
			@PathVariable String key,
			@AuthenticationPrincipal Jwt principal) {
		
		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}
		
		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.getObject(bucket, key, identity);
	}
	
	/**
	 * Put object to S3
	 */
	@PostMapping(path="/object/{bucket}/{key}")
	public Mono<String> putObject(
			@PathVariable String bucket,
			@PathVariable String key,
			@RequestBody byte[] content,
			@AuthenticationPrincipal Jwt principal) {
		
		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}
		
		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.putObject(bucket, key, content, identity);
	}
	
	/**
	 * Delete object from S3
	 */
	@DeleteMapping(path="/object/{bucket}/{key}")
	public Mono<Void> deleteObject(
			@PathVariable String bucket,
			@PathVariable String key,
			@AuthenticationPrincipal Jwt principal) {
		
		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}
		
		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.deleteObject(bucket, key, identity);
	}
}
