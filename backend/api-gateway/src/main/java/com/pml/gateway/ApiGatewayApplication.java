package com.pml.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway Application
 *
 * Entry point for the API Gateway service that handles:
 * - Request routing to microservices (Catalog, Booking, Identity)
 * - JWT authentication validation
 * - Rate limiting
 * - Circuit breaker patterns
 * - Request/response logging
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
