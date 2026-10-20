package com.ecommerce.backend.controller;

import com.ecommerce.backend.dto.ReviewDto;
import com.ecommerce.backend.entity.Review;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.ReviewRepository;
import com.ecommerce.backend.repository.ShopRepository;
import com.ecommerce.backend.security.ShopContext;
import com.ecommerce.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;

    private Long getShopId() {
        Long shopId = ShopContext.getShopId();
        if (shopId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop context is missing");
        }
        return shopId;
    }

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) auth.getPrincipal()).getId();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
    }

    @PostMapping("/reviews")
    public ReviewDto createReview(@jakarta.validation.Valid @RequestBody ReviewDto reviewDto) {
        Long shopId = getShopId();
        Long userId = getAuthenticatedUserId();

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));

        Review review = new Review();
        review.setUserId(userId);
        review.setItemId(reviewDto.getItemId());
        review.setRating(reviewDto.getRating());
        review.setComment(reviewDto.getComment());
        review.setCreatedAt(LocalDateTime.now());
        review.setShop(shop);

        review = reviewRepository.save(review);
        return mapToDto(review);
    }

    @GetMapping("/items/{id}/reviews")
    public List<ReviewDto> getItemReviews(@PathVariable("id") Long itemId) {
        return reviewRepository.findByItemIdOrderByCreatedAtDesc(itemId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    private ReviewDto mapToDto(Review review) {
        ReviewDto dto = new ReviewDto();
        dto.setId(review.getId());
        dto.setUserId(review.getUserId());
        dto.setItemId(review.getItemId());
        dto.setRating(review.getRating());
        dto.setComment(review.getComment());
        dto.setCreatedAt(review.getCreatedAt());
        return dto;
    }
}
