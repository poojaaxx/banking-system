package com.bankingdemo; 

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// We always provide our own explicit AuthenticationManager beans (one for customers, one
// for admins, each backed by its own UserDetailsService) -- Boot's default fallback
// UserDetailsService/AuthenticationManager autoconfiguration is never used and only produces
// a confusing startup warning when it sees more than one UserDetailsService bean.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class BankingBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankingBackendApplication.class, args);
	}

}
