package com.ironbucket.brazznossel.controller;

import com.ironbucket.brazznossel.model.NormalizedIdentity;
import com.ironbucket.brazznossel.service.S3ProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/s3")
public class S3Controller {
	
	private final S3ProxyService s3ProxyService;
	
	// Constructor injection for testability
	public S3Controller(S3ProxyService s3ProxyService) {
		this.s3ProxyService = s3ProxyService;
	}
	
	/**
	 * Convert JWT to NormalizedIdentity
	 */
	private NormalizedIdentity extractIdentity(Jwt jwt) {
		String username = jwt.getClaimAsString("preferred_username");
		if (username == null) {
			username = jwt.getSubject();
		}
		
		String tenant = resolveTenant(jwt);
		
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

	private String resolveTenant(Jwt jwt) {
		String tenant = firstNonBlank(
				jwt.getClaimAsString("tenant"),
				jwt.getClaimAsString("tenant_id"),
				jwt.getClaimAsString("tenantId"));
		if (tenant != null) {
			return tenant;
		}

		Object organizationClaim = jwt.getClaims().get("organization");
		if (organizationClaim instanceof String org && !org.isBlank()) {
			return org;
		}
		if (organizationClaim instanceof Map<?, ?> organizationMap) {
			Object id = organizationMap.get("id");
			if (id instanceof String idValue && !idValue.isBlank()) {
				return idValue;
			}
			Object name = organizationMap.get("name");
			if (name instanceof String nameValue && !nameValue.isBlank()) {
				return nameValue;
			}
		}

		String orgAlias = firstNonBlank(
				jwt.getClaimAsString("organization_id"),
				jwt.getClaimAsString("org"),
				jwt.getClaimAsString("org_id"),
				jwt.getClaimAsString("kc_org"));
		if (orgAlias != null) {
			return orgAlias;
		}

		List<String> organizations = jwt.getClaimAsStringList("organizations");
		if (organizations != null && !organizations.isEmpty() && organizations.get(0) != null && !organizations.get(0).isBlank()) {
			return organizations.get(0);
		}

		List<String> groups = jwt.getClaimAsStringList("groups");
		if (groups != null) {
			for (String group : groups) {
				if (group == null || group.isBlank()) {
					continue;
				}
				if (group.startsWith("org:")) {
					return group.substring("org:".length());
				}
				if (group.startsWith("/org/")) {
					return group.substring("/org/".length());
				}
				if (group.startsWith("/orgs/")) {
					return group.substring("/orgs/".length());
				}
			}
		}

		return "default";
	}

	private String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
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

	@PostMapping(path="/bucket/{bucket}")
	public Mono<String> createBucket(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.createBucket(bucket, identity);
	}

	@DeleteMapping(path="/bucket/{bucket}")
	public Mono<Void> deleteBucket(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.deleteBucket(bucket, identity);
	}

	@RequestMapping(path="/bucket/{bucket}", method = RequestMethod.HEAD)
	public Mono<String> headBucket(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.headBucket(bucket, identity);
	}

	@GetMapping(path="/objects/{bucket}")
	public Mono<String> listObjects(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.listObjects(bucket, identity);
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

	@RequestMapping(path="/object/{bucket}/{key}", method = RequestMethod.HEAD)
	public Mono<String> headObject(
			@PathVariable String bucket,
			@PathVariable String key,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.headObject(bucket, key, identity);
	}

	@GetMapping(path="/object/{bucket}/{key}/range")
	public Mono<byte[]> getObjectRange(
			@PathVariable String bucket,
			@PathVariable String key,
			@RequestParam long start,
			@RequestParam long end,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.getObjectRange(bucket, key, start, end, identity);
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

	@GetMapping(path="/object/{bucket}/{key}/version/{versionId}")
	public Mono<byte[]> getObjectVersion(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String versionId,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.getObjectVersion(bucket, key, versionId, identity);
	}

	@DeleteMapping(path="/object/{bucket}/{key}/version/{versionId}")
	public Mono<Void> deleteObjectVersion(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String versionId,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.deleteObjectVersion(bucket, key, versionId, identity);
	}

	@GetMapping(path="/object-versions/{bucket}")
	public Mono<String> listObjectVersions(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.listObjectVersions(bucket, identity);
	}

	@PostMapping(path="/multipart/{bucket}/{key}/initiate")
	public Mono<String> initiateMultipartUpload(
			@PathVariable String bucket,
			@PathVariable String key,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.initiateMultipartUpload(bucket, key, identity);
	}

	@PostMapping(path="/multipart/{bucket}/{key}/{uploadId}/part/{partNumber}")
	public Mono<String> uploadPart(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String uploadId,
			@PathVariable int partNumber,
			@RequestBody byte[] content,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.uploadPart(bucket, key, uploadId, partNumber, content, identity);
	}

	@PostMapping(path="/multipart/{bucket}/{key}/{uploadId}/complete")
	public Mono<String> completeMultipartUpload(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String uploadId,
			@RequestBody List<CompletedPart> parts,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.completeMultipartUpload(bucket, key, uploadId, parts, identity);
	}

	@DeleteMapping(path="/multipart/{bucket}/{key}/{uploadId}")
	public Mono<Void> abortMultipartUpload(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String uploadId,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.abortMultipartUpload(bucket, key, uploadId, identity);
	}

	@GetMapping(path="/multipart/{bucket}")
	public Mono<String> listMultipartUploads(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.listMultipartUploads(bucket, identity);
	}

	@GetMapping(path="/multipart/{bucket}/{key}/{uploadId}/parts")
	public Mono<String> listParts(
			@PathVariable String bucket,
			@PathVariable String key,
			@PathVariable String uploadId,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.listParts(bucket, key, uploadId, identity);
	}

	@GetMapping(path="/bucket/{bucket}/versioning")
	public Mono<String> getBucketVersioning(
			@PathVariable String bucket,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.getBucketVersioning(bucket, identity);
	}

	@PutMapping(path="/bucket/{bucket}/versioning")
	public Mono<String> putBucketVersioning(
			@PathVariable String bucket,
			@RequestParam String status,
			@AuthenticationPrincipal Jwt principal) {

		if (principal == null) {
			return Mono.error(new IllegalStateException("No authentication principal found"));
		}

		NormalizedIdentity identity = extractIdentity(principal);
		return s3ProxyService.putBucketVersioning(bucket, status, identity);
	}
}
