package com.fixhomi.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.fixhomi.auth.config.JwtProperties;
import com.fixhomi.auth.config.FixhomiProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, FixhomiProperties.class})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
