package com.ecommerce.backend.controller;

import com.ecommerce.backend.security.UserDetailsImpl;
import com.ecommerce.backend.service.AiPhoneticService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/phonetics")
@RequiredArgsConstructor
public class AdminPhoneticController {

    private final AiPhoneticService aiPhoneticService;

    @PostMapping("/update-batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updatePhoneticsBatch(@AuthenticationPrincipal UserDetailsImpl user) {
        int updatedCount = aiPhoneticService.batchUpdatePhonetics(user.getShopId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("updatedCount", updatedCount);
        response.put("message", "Successfully updated phonetics for " + updatedCount + " items.");
        
        return ResponseEntity.ok(response);
    }
}
