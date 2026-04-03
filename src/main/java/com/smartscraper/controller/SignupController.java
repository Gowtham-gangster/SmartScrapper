package com.smartscraper.controller;

import com.smartscraper.entity.User;
import com.smartscraper.repository.UserRepository;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SignupController {

    private static final Logger logger = LoggerFactory.getLogger(SignupController.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SignupController(UserRepository userRepository, UserService userService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Handle user registration.
     * 
     * @param username User's chosen username
     * @param email User's email address
     * @param password User's password (will be hashed)
     * @param confirmPassword Password confirmation
     * @param redirectAttributes For flash messages
     * @return Redirect to login page on success, or back to signup on error
     */
    @PostMapping("/signup")
    public String signup(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes) {

        logger.info("Signup attempt for username: {}", username);

        // Validation: Check if fields are empty
        if (username == null || username.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Username is required");
            return "redirect:/signup";
        }

        if (email == null || email.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email is required");
            return "redirect:/signup";
        }

        if (password == null || password.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password is required");
            return "redirect:/signup";
        }

        // Validation: Check password length
        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 6 characters long");
            return "redirect:/signup";
        }

        // Validation: Check if passwords match
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Passwords do not match");
            return "redirect:/signup";
        }

        // Validation: Check if username already exists using UserService
        if (userService.usernameExists(username.trim())) {
            logger.warn("Signup failed: Username '{}' already exists", username);
            redirectAttributes.addFlashAttribute("errorMessage", "Username already taken");
            return "redirect:/signup";
        }

        // Validation: Check if email already exists using UserService
        if (userService.emailExists(email.trim())) {
            logger.warn("Signup failed: Email '{}' already registered", email);
            redirectAttributes.addFlashAttribute("errorMessage", "Email already registered");
            return "redirect:/signup";
        }

        try {
            // Hash the password using BCrypt
            String passwordHash = passwordEncoder.encode(password);
            logger.debug("Password hashed successfully for user: {}", username);

            // Create new user
            User newUser = new User(username.trim(), email.trim(), passwordHash);

            // Save user to database
            User savedUser = userRepository.save(newUser);
            logger.info("User registered successfully: {} (ID: {})", savedUser.getUsername(), savedUser.getId());

            // Redirect to login with success message
            redirectAttributes.addFlashAttribute("successMessage", 
                "Registration successful! Please login with your credentials.");
            return "redirect:/login";

        } catch (Exception e) {
            logger.error("Error during user registration: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", 
                "An error occurred during registration. Please try again.");
            return "redirect:/signup";
        }
    }
}
