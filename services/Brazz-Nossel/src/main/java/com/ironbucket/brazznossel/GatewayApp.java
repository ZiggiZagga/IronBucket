package com.ironbucket.brazznossel;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
@ComponentScan(
		{			
			"com.ironbucket.brazznossel.config",
			"com.ironbucket.brazznossel.controller",
		}
	)
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApp {
	public static void main(String[] args) {
		SpringApplication.run(GatewayApp.class, args);
	}
}
