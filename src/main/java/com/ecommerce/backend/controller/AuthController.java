package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.auth.*;
import com.ecommerce.backend.entity.OtpPurpose;
import com.ecommerce.backend.entity.RefreshToken;
import com.ecommerce.backend.entity.Role;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.security.JwtService;
import com.ecommerce.backend.security.UserDetailsImpl;
import com.ecommerce.backend.service.CartService;
import com.ecommerce.backend.service.OtpService;
import com.ecommerce.backend.service.RefreshTokenService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CartService cartService;
    private final OtpService otpService;
    private final com.ecommerce.backend.repository.ShopRepository shopRepository;

    // IP-based rate limiting for login and register (5 attempts per minute)
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket resolveBucket(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return loginBuckets.computeIfAbsent(ip, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))))
                .build());
    }

    /**
     * POST /auth/login
     * Authenticates credentials and issues a JWT access + refresh token pair.
     * SECURITY: Login is blocked if the user's email is not verified.
     */
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(
            HttpServletRequest request,
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @Valid @RequestBody LoginRequest loginRequest) {

        Bucket bucket = resolveBucket(request);
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many login attempts. Please try again later.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (org.springframework.security.authentication.DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Your account is pending Super Admin approval.");
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        // Block unverified users from obtaining a full JWT
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        if (!user.isEmailVerified()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Email not verified. Please check your inbox for a verification code.");
        }

        // SECURITY: Prevent cross-shop logins. A user belonging to Shop A cannot login to Shop B.
        Long requestedShopId = com.ecommerce.backend.security.ShopContext.getShopId();
        if (requestedShopId != null && userDetails.getShopId() != null && !requestedShopId.equals(userDetails.getShopId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied. You do not belong to this shop.");
        }

        String jwt = jwtService.generateJwtToken(authentication);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(userDetails.getId());

        if (sessionId != null) {
            cartService.mergeCart(com.ecommerce.backend.security.ShopContext.getShopId(), sessionId, userDetails.getId());
        }

        String role = userDetails.getAuthorities().iterator().next().getAuthority();
        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken(),
                userDetails.getId(), user.getName(), userDetails.getUsername(), role));
    }

    /**
     * POST /auth/register
     * Creates a new user account with emailVerified=false.
     * Generates and emails an OTP verification code.
     * DOES NOT return a JWT — user must verify email first via /auth/verify-email.
     *
     * DESIGN DECISION: Option (a) — no JWT until email verified.
     * This prevents unverified accounts from accessing any authenticated features.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            HttpServletRequest request,
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @Valid @RequestBody RegisterRequest signUpRequest) {

        Bucket bucket = resolveBucket(request);
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many registration attempts. Please try again later.");
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        // Create user with emailVerified=false
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setEmail(signUpRequest.getEmail());
        user.setPasswordHash(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setCreatedAt(LocalDateTime.now());
        user.setEmailVerified(false);
        if (com.ecommerce.backend.security.ShopContext.getShopId() != null) {
            user.setShop(shopRepository.findById(com.ecommerce.backend.security.ShopContext.getShopId()).orElse(null));
        }
        userRepository.save(user);

        // Generate and send OTP (errors are caught to avoid exposing internals)
        try {
            Long shopId = com.ecommerce.backend.security.ShopContext.getShopId();
            if (shopId == null && user.getShop() != null) {
                shopId = user.getShop().getId();
            }
            otpService.generateAndSendOtp(
                    shopId,
                    signUpRequest.getEmail(), 
                    OtpPurpose.EMAIL_VERIFICATION, 
                    user.getName()
            );
        } catch (Exception e) {
            logger.error("Failed to dispatch registration OTP to {}: {}", signUpRequest.getEmail(), e.getMessage(), e);
        }

        return ResponseEntity.ok(new RegisterPendingResponse(
                "Verification email sent. Please check your inbox.", signUpRequest.getEmail()));
    }

    /**
     * POST /auth/verify-email
     * Validates the 6-digit OTP sent to the user's email after registration.
     * On success: marks emailVerified=true and issues JWT access + refresh tokens.
     * This is the step that completes the login flow for new registrations.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(
            @RequestHeader(value = "X-Session-ID", required = false) String sessionId,
            @Valid @RequestBody VerifyEmailRequest verifyRequest) {

        try {
            Long shopId = com.ecommerce.backend.security.ShopContext.getShopId();
            if (shopId == null) {
                User u = userRepository.findByEmail(verifyRequest.getEmail()).orElse(null);
                if (u != null && u.getShop() != null) {
                    shopId = u.getShop().getId();
                }
            }
            otpService.validateAndConsumeOtp(
                    shopId,
                    verifyRequest.getEmail(), 
                    verifyRequest.getCode(), 
                    OtpPurpose.EMAIL_VERIFICATION
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        User user = userRepository.findByEmail(verifyRequest.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        if (user.getRole() == Role.SHOP_ADMIN && user.getShop() != null && user.getShop().getStatus() != com.ecommerce.backend.entity.ShopStatus.ACTIVE) {
            return ResponseEntity.ok(Map.of(
                "message", "Email verified successfully. Your account is pending Super Admin approval.",
                "pendingApproval", true
            ));
        }

        // Now issue the JWT — login is effectively complete here
        String jwt = jwtService.generateJwtTokenFromUser(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        if (sessionId != null) {
            cartService.mergeCart(com.ecommerce.backend.security.ShopContext.getShopId(), sessionId, user.getId());
        }

        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken.getToken(),
                user.getId(), user.getName(), user.getEmail(), "ROLE_" + user.getRole().name()));
    }

    /**
     * POST /auth/resend-otp
     * Resends an OTP for an active pending verification.
     * Enforces a 60-second cooldown and a maximum of 5 resends per OTP lifetime.
     * Shared by both EMAIL_VERIFICATION and PASSWORD_RESET flows.
     */
    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequest resendRequest) {
        try {
            Long shopId = com.ecommerce.backend.security.ShopContext.getShopId();
            if (shopId == null) {
                User u = userRepository.findByEmail(resendRequest.getEmail()).orElse(null);
                if (u != null && u.getShop() != null) {
                    shopId = u.getShop().getId();
                }
            }
            otpService.resendOtp(shopId, resendRequest.getEmail(), resendRequest.getPurpose());
            return ResponseEntity.ok("A new code has been sent to your email");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * POST /auth/forgot-password
     * Initiates the password reset flow by sending an OTP to the provided email.
     *
     * SECURITY: Always returns the same 200 response regardless of whether the email
     * exists in the system. This prevents email enumeration attacks — attackers cannot
     * determine which email addresses are registered by probing this endpoint.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Only send OTP if user actually exists — but always return same response (security)
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            try {
                Long shopId = com.ecommerce.backend.security.ShopContext.getShopId();
                if (shopId == null && user.getShop() != null) {
                    shopId = user.getShop().getId();
                }
                otpService.generateAndSendOtp(shopId, request.getEmail(), OtpPurpose.PASSWORD_RESET, user.getName());
            } catch (Exception e) {
                logger.error("Failed to dispatch password reset OTP to {}: {}", request.getEmail(), e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(
                "If an account with that email exists, a password reset code has been sent.");
    }

    /**
     * POST /auth/reset-password
     * Validates the OTP and sets a new password.
     * On success: invalidates ALL refresh tokens for the user (forces re-login everywhere).
     * This is a standard security measure — password reset means existing sessions are stale.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            Long shopId = com.ecommerce.backend.security.ShopContext.getShopId();
            if (shopId == null) {
                User u = userRepository.findByEmail(request.getEmail()).orElse(null);
                if (u != null && u.getShop() != null) {
                    shopId = u.getShop().getId();
                }
            }
            otpService.validateAndConsumeOtp(
                    shopId,
                    request.getEmail(), 
                    request.getCode(), 
                    OtpPurpose.PASSWORD_RESET
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("User not found");
        }

        // Update password with BCrypt hash (reusing the same PasswordEncoder as registration)
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Invalidate all refresh tokens — force re-login on all devices after password reset
        refreshTokenService.deleteByUserId(user.getId());

        return ResponseEntity.ok("Password reset successful. Please log in with your new password.");
    }

    /**
     * POST /auth/refresh
     * Exchanges a valid refresh token for a new JWT access token.
     */
    @PostMapping("/refresh")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtService.generateJwtTokenFromUser(user);
                    return ResponseEntity.ok(new TokenRefreshResponse(token, requestRefreshToken));
                })
                .orElseThrow(() -> new RuntimeException("Refresh token is not in database!"));
    }

    /**
     * POST /auth/logout
     * Invalidates the user's refresh token.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logoutUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            refreshTokenService.deleteByUserId(userDetails.getId());
        }
        return ResponseEntity.ok("Log out successful");
    }
}
