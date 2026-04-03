package com.smartscraper.config;

import com.smartscraper.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userDetailsService;

    @Autowired
    public SecurityConfig(UserService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Configure HTTP security.
     * - Form-based login
     * - Login page: /login
     * - Success redirect: /dashboard
     * - Logout enabled
     * - Public routes: /, /login, /signup, static resources
     * - All other routes require authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(authorize -> authorize
                // Public routes - no authentication required
                .requestMatchers("/", "/signup", "/login", "/css/**", "/js/**", "/images/**", 
                                "/actuator/health", "/actuator/info").permitAll()
                // Actuator endpoints require authentication
                .requestMatchers("/actuator/**").authenticated()
                // All other routes require authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                // Custom login page
                .loginPage("/login")
                // Redirect to dashboard after successful login
                .defaultSuccessUrl("/dashboard", true)
                // Allow everyone to access login page
                .permitAll()
            )
            .logout(logout -> logout
                // Redirect to home page after logout
                .logoutSuccessUrl("/")
                // Invalidate session
                .invalidateHttpSession(true)
                // Delete cookies
                .deleteCookies("JSESSIONID")
                // Allow everyone to logout
                .permitAll()
            )
            .sessionManagement(session -> session
                // Maximum one session per user
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
            )
            .headers(headers -> headers
                // Security headers
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.disable())
                .contentTypeOptions(contentType -> contentType.disable())
            );
        
        return http.build();
    }

    /**
     * Configure authentication provider to use database authentication.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication manager bean.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Password encoder bean using BCrypt.
     * BCrypt automatically handles salt generation and is resistant to rainbow table attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
