package com.ironbucket.brazznossel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/s3")
public class S3Controller {
	@GetMapping(path="/dev")
	public  Mono<String> helloDev(/*@AuthenticationPrincipal Jwt principal*/) {	
		String user = "UNKNOWN";
		/*		
		if(principal != null) {
			user = principal.getClaimAsString("preferred_username");
		}*/
        return Mono.just("Hello brazznossel dev user: "+user);
	}
	@GetMapping(path="/admin")
	public  Mono<String> helloAdmin(/*@AuthenticationPrincipal Jwt principal*/) {	
		String user = "UNKNOWN";
		/*
		if(principal != null) {
			user = principal.getClaimAsString("preferred_username");
		}*/
        return Mono.just("Hello brazznossel admin user: "+user);
	}
}
