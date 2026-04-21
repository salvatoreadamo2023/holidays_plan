package com.holidays.plan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults()) // <--- AGGIUNGI QUESTA RIGA
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Permetti esplicitamente le richieste OPTIONS (Pre-flight)
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll() 
                
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/users/all").hasAnyRole("ADMIN", "MANAGER", "USER")
                .requestMatchers("/api/users/create").hasRole("ADMIN")
                .requestMatchers("/api/holidays/manager/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                .requestMatchers("/api/holidays/status/**").hasAnyRole("MANAGER", "ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    // AGGIUNGI QUESTO BEAN SUBITO SOTTO
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("*")); // Permette l'accesso da qualsiasi dispositivo
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept"));
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt per gestire le password criptate nel Database
        return new BCryptPasswordEncoder();
    }
}