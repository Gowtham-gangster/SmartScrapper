package com.smartscraper.controller;

import com.smartscraper.entity.User;
import com.smartscraper.repository.UserRepository;
import com.smartscraper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Optional;

/**
 * Controller for managing user profile.
 * Allows users to view and update their profile information.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    private final UserService userService;
    private final UserRepository userRepository;

    @Autowired
    public ProfileController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * Display profile edit page.
     * 
     * @param principal Current logged-in user
     * @param model Spring MVC model
     * @return profile-edit template
     */
    @GetMapping("/edit")
    public String showEditProfile(Principal principal, Model model) {
        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) accessed profile edit page",
                currentUser.getUsername(), currentUser.getId());

        // Add user information to model
        model.addAttribute("user", currentUser);
        model.addAttribute("userId", currentUser.getId());
        model.addAttribute("username", currentUser.getUsername());
        model.addAttribute("email", currentUser.getEmail());

        return "profile-edit";
    }

    /**
     * Update user profile.
     * Validates and updates username and/or email.
     * 
     * @param principal Current logged-in user
     * @param newUsername New username (optional)
     * @param newEmail New email (optional)
     * @param redirectAttributes For flash messages
     * @return Redirect to profile edit page
     */
    @PostMapping("/update")
    public String updateProfile(
            Principal principal,
            @RequestParam(value = "username", required = false) String newUsername,
            @RequestParam(value = "email", required = false) String newEmail,
            RedirectAttributes redirectAttributes) {

        User currentUser = userService.getCurrentUser(principal);
        logger.info("User {} (ID: {}) attempting to update profile",
                currentUser.getUsername(), currentUser.getId());

        boolean updated = false;
        StringBuilder successMessage = new StringBuilder("Profile updated successfully: ");

        // Validate and update username
        if (newUsername != null && !newUsername.trim().isEmpty()) {
            String trimmedUsername = newUsername.trim();

            // Check if username is different
            if (!trimmedUsername.equals(currentUser.getUsername())) {
                // Validate username
                if (trimmedUsername.length() < 3) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Username must be at least 3 characters long.");
                    return "redirect:/profile/edit";
                }

                if (trimmedUsername.length() > 50) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Username must not exceed 50 characters.");
                    return "redirect:/profile/edit";
                }

                // Check if username already exists
                Optional<User> existingUser = userRepository.findByUsername(trimmedUsername);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(currentUser.getId())) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Username '" + trimmedUsername + "' is already taken.");
                    return "redirect:/profile/edit";
                }

                // Update username
                currentUser.setUsername(trimmedUsername);
                updated = true;
                successMessage.append("Username changed to '").append(trimmedUsername).append("'. ");
                logger.info("User ID {} changed username to '{}'", currentUser.getId(), trimmedUsername);
            }
        }

        // Validate and update email
        if (newEmail != null && !newEmail.trim().isEmpty()) {
            String trimmedEmail = newEmail.trim().toLowerCase();

            // Check if email is different
            if (!trimmedEmail.equals(currentUser.getEmail())) {
                // Validate email format
                if (!isValidEmail(trimmedEmail)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Invalid email format.");
                    return "redirect:/profile/edit";
                }

                if (trimmedEmail.length() > 100) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Email must not exceed 100 characters.");
                    return "redirect:/profile/edit";
                }

                // Check if email already exists
                Optional<User> existingUser = userRepository.findByEmail(trimmedEmail);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(currentUser.getId())) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Email '" + trimmedEmail + "' is already registered.");
                    return "redirect:/profile/edit";
                }

                // Update email
                currentUser.setEmail(trimmedEmail);
                updated = true;
                successMessage.append("Email changed to '").append(trimmedEmail).append("'.");
                logger.info("User ID {} changed email to '{}'", currentUser.getId(), trimmedEmail);
            }
        }

        // Save changes if any updates were made
        if (updated) {
            userRepository.save(currentUser);
            redirectAttributes.addFlashAttribute("successMessage", successMessage.toString());
            logger.info("User ID {} profile updated successfully", currentUser.getId());
        } else {
            redirectAttributes.addFlashAttribute("infoMessage",
                    "No changes were made to your profile.");
            logger.info("User ID {} submitted profile update with no changes", currentUser.getId());
        }

        return "redirect:/profile/edit";
    }

    /**
     * Validate email format.
     * 
     * @param email Email to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Simple email validation regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
}
