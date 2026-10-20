package com.ecommerce.backend.service;

import com.ecommerce.backend.entity.OtpCode;
import com.ecommerce.backend.entity.OtpPurpose;
import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.OtpCodeRepository;
import com.ecommerce.backend.repository.UserRepository;
import com.ecommerce.backend.repository.ShopRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int COOLDOWN_SECONDS = 60;
    private static final int MAX_RESEND_COUNT = 5;

    private final OtpCodeRepository otpCodeRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<String, Bucket> otpRateBuckets = new ConcurrentHashMap<>();

    private Bucket resolveOtpBucket(String email) {
        return otpRateBuckets.computeIfAbsent(email.toLowerCase(), k ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofHours(1))))
                .build());
    }

    @Transactional
    public void generateAndSendOtp(Long shopId, String email, OtpPurpose purpose, String userName) {
        Bucket bucket = resolveOtpBucket(email);
        if (!bucket.tryConsume(1)) {
            throw new RuntimeException("Too many verification requests. Please try again later.");
        }

        if (shopId != null) {
            otpCodeRepository.deleteByShopIdAndEmailAndPurpose(shopId, email, purpose);
        } else {
            otpCodeRepository.deleteByEmailAndPurpose(email, purpose);
        }

        String code = generateCode();

        Shop shop = (shopId != null) ? shopRepository.findById(shopId).orElse(null) : null;
        String shopName = (shop != null && shop.getDisplayName() != null) ? shop.getDisplayName() : "Store";

        OtpCode otpCode = new OtpCode();
        otpCode.setShop(shop);
        otpCode.setEmail(email);
        otpCode.setCode(code);
        otpCode.setPurpose(purpose);
        otpCode.setCreatedAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpCode.setUsed(false);
        otpCode.setResendCount(0);
        otpCode.setLastSentAt(LocalDateTime.now());
        otpCodeRepository.save(otpCode);

        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            emailService.sendVerificationEmail(email, userName, code, shopName);
        } else {
            emailService.sendPasswordResetEmail(email, userName, code, shopName);
        }

        logger.info("OTP dispatched for purpose={} to address ending in {}", purpose, maskEmail(email));
    }

    @Transactional
    public OtpCode validateAndConsumeOtp(Long shopId, String email, String code, OtpPurpose purpose) {
        Optional<OtpCode> otpOpt;
        if (shopId != null) {
            otpOpt = otpCodeRepository.findFirstByShopIdAndEmailAndPurposeOrderByCreatedAtDesc(shopId, email, purpose);
            if (otpOpt.isEmpty()) {
                otpOpt = otpCodeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
            }
        } else {
            otpOpt = otpCodeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
        }

        if (otpOpt.isEmpty()) {
            throw new RuntimeException("No verification code found. Please request a new one.");
        }

        OtpCode otpCode = otpOpt.get();

        if (otpCode.isUsed()) {
            throw new RuntimeException("This code has already been used.");
        }
        if (LocalDateTime.now().isAfter(otpCode.getExpiresAt())) {
            throw new RuntimeException("This code has expired. Please request a new one.");
        }
        if (!otpCode.getCode().equals(code)) {
            throw new RuntimeException("Invalid verification code.");
        }

        otpCode.setUsed(true);
        otpCodeRepository.save(otpCode);
        return otpCode;
    }

    @Transactional
    public void resendOtp(Long shopId, String email, OtpPurpose purpose) {
        Optional<OtpCode> otpOpt;
        if (shopId != null) {
            otpOpt = otpCodeRepository.findFirstByShopIdAndEmailAndPurposeOrderByCreatedAtDesc(shopId, email, purpose);
            if (otpOpt.isEmpty()) {
                otpOpt = otpCodeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
            }
        } else {
            otpOpt = otpCodeRepository.findFirstByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);
        }

        if (otpOpt.isEmpty()) {
            throw new RuntimeException("No active OTP session found. Please register or initiate a password reset first.");
        }

        OtpCode otpCode = otpOpt.get();
        if (otpCode.isUsed()) {
            throw new RuntimeException("This code has already been used.");
        }

        if (LocalDateTime.now().isBefore(otpCode.getLastSentAt().plusSeconds(COOLDOWN_SECONDS))) {
            throw new RuntimeException("Please wait 60 seconds before requesting another code.");
        }
        if (otpCode.getResendCount() >= MAX_RESEND_COUNT) {
            throw new RuntimeException("Maximum resend limit reached. Please contact support or start over.");
        }

        otpCode.setResendCount(otpCode.getResendCount() + 1);
        otpCode.setLastSentAt(LocalDateTime.now());
        otpCode.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otpCodeRepository.save(otpCode);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop shop = (shopId != null) ? shopRepository.findById(shopId).orElse(null) : user.getShop();
        String shopName = (shop != null && shop.getDisplayName() != null) ? shop.getDisplayName() : "Store";

        if (purpose == OtpPurpose.EMAIL_VERIFICATION) {
            emailService.sendVerificationEmail(email, user.getName(), otpCode.getCode(), shopName);
        } else {
            emailService.sendPasswordResetEmail(email, user.getName(), otpCode.getCode(), shopName);
        }

        logger.info("OTP resent for purpose={} to address ending in {}", purpose, maskEmail(email));
    }

    private String generateCode() {
        int code = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(code);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "*****";
        return email.substring(email.indexOf("@") - 1);
    }
}
