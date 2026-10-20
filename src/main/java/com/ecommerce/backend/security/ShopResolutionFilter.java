package com.ecommerce.backend.security;

import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.entity.ShopStatus;
import com.ecommerce.backend.repository.ShopRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ShopResolutionFilter extends OncePerRequestFilter {

    public static final String SHOP_KEY_HEADER = "X-Shop-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ShopRepository shopRepository;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // Skip CORS preflight — browsers send OPTIONS without custom headers
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            Long shopId = resolveShopId(request);
            if (shopId != null) {
                ShopContext.setShopId(shopId);
            }
            filterChain.doFilter(request, response);
        } finally {
            ShopContext.clear();
        }
    }

    private Long resolveShopId(HttpServletRequest request) {

        // ── Priority 1: X-Shop-Key header ─────────────────────────────────────
        // Always sent by both the Android app and the Angular frontend.
        // This is the most reliable signal.
        String shopKey = request.getHeader(SHOP_KEY_HEADER);
        if (StringUtils.hasText(shopKey)) {
            Optional<Shop> shopOpt = shopRepository.findByShopKey(shopKey);
            if (shopOpt.isPresent() && shopOpt.get().getStatus() == ShopStatus.ACTIVE) {
                return shopOpt.get().getId();
            }
        }

        // ── Priority 2: Authenticated user's shopId from UserDetailsImpl ───────
        // For SHOP_ADMIN users whose shopId is embedded in their token and
        // loaded into the SecurityContext by JwtAuthenticationFilter.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            Long shopId = ((UserDetailsImpl) auth.getPrincipal()).getShopId();
            if (shopId != null) {
                return shopId;
            }
        }

        // ── Priority 3: shopId claim from JWT token directly ──────────────────
        // Fallback when SecurityContext is not yet populated or the user's
        // UserDetailsImpl doesn't carry shopId (e.g. old tokens, SUPER_ADMIN).
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith(BEARER_PREFIX)) {
            String jwt = headerAuth.substring(BEARER_PREFIX.length());
            try {
                Long shopId = jwtService.getShopIdFromJwtToken(jwt);
                if (shopId != null) {
                    return shopId;
                }
            } catch (Exception ignored) {
                // Token invalid/expired — no shopId from JWT
            }
        }

        return null;
    }
}
