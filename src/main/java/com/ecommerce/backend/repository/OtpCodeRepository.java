package com.ecommerce.backend.repository;

import com.ecommerce.backend.entity.OtpCode;
import com.ecommerce.backend.entity.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findFirstByShopIdAndEmailAndPurposeOrderByCreatedAtDesc(Long shopId, String email, OtpPurpose purpose);
    void deleteByShopIdAndEmailAndPurpose(Long shopId, String email, OtpPurpose purpose);

    Optional<OtpCode> findFirstByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);
    void deleteByEmailAndPurpose(String email, OtpPurpose purpose);
}
