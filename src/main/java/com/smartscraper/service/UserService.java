package com.smartscraper.service;

import com.smartscraper.entity.User;
import com.smartscraper.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Service for user-related operations.
 * Provides methods to retrieve and manage user data.
 * Also implements Spring Security's UserDetailsService for authentication.
 */
@Service
public class UserService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load user by username for Spring Security authentication.
     * Implements UserDetailsService interface.
     * 
     * @param username The username to search for
     * @return UserDetails object containing user information
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading user by username for authentication: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("User not found during authentication: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        logger.debug("User authenticated: {} (ID: {})", user.getUsername(), user.getId());

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                getAuthorities()
        );
    }

    /**
     * Get user authorities/roles for Spring Security.
     * Currently assigns ROLE_USER to all users.
     * Can be extended to support multiple roles from database.
     * 
     * @return Collection of granted authorities
     */
    private Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * Get the currently logged-in user from Principal.
     * 
     * @param principal Spring Security Principal containing username
     * @return User entity
     * @throws UsernameNotFoundException if user not found
     */
    public User getCurrentUser(Principal principal) {
        if (principal == null) {
            logger.error("Principal is null - user not authenticated");
            throw new UsernameNotFoundException("User not authenticated");
        }

        String username = principal.getName();
        logger.debug("Retrieving current user: {}", username);

        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.error("User not found in database: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });
    }

    /**
     * Get user by ID.
     * 
     * @param userId User ID
     * @return Optional containing user if found
     */
    public Optional<User> getUserById(Long userId) {
        logger.debug("Retrieving user by ID: {}", userId);
        return userRepository.findById(userId);
    }

    /**
     * Get user by username.
     * 
     * @param username Username
     * @return Optional containing user if found
     */
    public Optional<User> getUserByUsername(String username) {
        logger.debug("Retrieving user by username: {}", username);
        return userRepository.findByUsername(username);
    }

    /**
     * Get user by email.
     * 
     * @param email Email address
     * @return Optional containing user if found
     */
    public Optional<User> getUserByEmail(String email) {
        logger.debug("Retrieving user by email: {}", email);
        return userRepository.findByEmail(email);
    }

    /**
     * Update user information.
     * 
     * @param user User entity to update
     * @return Updated user entity
     */
    public User updateUser(User user) {
        logger.info("Updating user: {} (ID: {})", user.getUsername(), user.getId());
        return userRepository.save(user);
    }

    /**
     * Check if username exists.
     * 
     * @param username Username to check
     * @return true if exists, false otherwise
     */
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Check if email exists.
     * 
     * @param email Email to check
     * @return true if exists, false otherwise
     */
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }
}
