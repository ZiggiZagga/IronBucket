# Pactum-Scroll

Shared Java library for IronBucket service contracts and cross-cutting behavior.

It centralizes:
- shared error contracts (`ApiErrorResponse`)
- shared WebFlux error handling base classes
- shared request-correlation filter behavior
- shared cache, resilience, and WebClient configuration helpers

Services consume this module as a Maven dependency:

```xml
<dependency>
	<groupId>com.ironbucket</groupId>
	<artifactId>pactum-scroll</artifactId>
	<version>4.0.1</version>
</dependency>
```
