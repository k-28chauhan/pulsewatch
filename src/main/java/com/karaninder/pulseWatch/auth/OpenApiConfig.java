package com.karaninder.pulseWatch.auth;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button to Swagger UI: paste the token from /api/auth/login and every call carries it. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI pulseWatchOpenApi() {
		return new OpenAPI()
				.info(new Info().title("PulseWatch API").version("0.1"))
				.components(new Components().addSecuritySchemes("bearerAuth",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
	}
}
