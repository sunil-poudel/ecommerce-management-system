package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.AddToCartRequest;
import com.ecommerce.backend.dto.CartDto;
import com.ecommerce.backend.dto.CartItemDto;
import com.ecommerce.backend.entity.Cart;
import com.ecommerce.backend.entity.CartItem;
import com.ecommerce.backend.entity.ItemUnit;
import com.ecommerce.backend.entity.Shop;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ItemRepository;
import com.ecommerce.backend.repository.ItemUnitRepository;
import com.ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ecommerce.backend.security.UserDetailsImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final DiscountCalculationService discountCalculationService;
    private final ShopRepository shopRepository;

    @Transactional
    public CartDto getCartDto(Long shopId, String sessionId) {
        Cart cart = getOrCreateCart(shopId, sessionId);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto addToCart(Long shopId, String sessionId, AddToCartRequest request) {
        Cart cart = getOrCreateCart(shopId, sessionId);
        
        // Handle legacy fallback
        if (request.getItemUnitId() == null && request.getItemId() != null) {
            com.ecommerce.backend.entity.Item item = itemRepository.findByIdAndShopId(request.getItemId(), shopId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
            ItemUnit primary = item.getUnits().stream()
                .filter(ItemUnit::isPrimary)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Item has no primary unit"));
            request.setItemUnitId(primary.getId());
        }

        ItemUnit unit = itemUnitRepository.findById(request.getItemUnitId())
                .orElseThrow(() -> new IllegalArgumentException("Item unit not found"));

        if (!unit.getShop().getId().equals(shopId)) {
            throw new IllegalArgumentException("Unit belongs to a different shop.");
        }

        if (unit.getStockQuantity() < request.getQuantity()) {
            throw new IllegalStateException("Not enough stock available for item unit: " + unit.getItem().getName() + " - " + unit.getLabel());
        }

        BigDecimal currentEffectivePrice = discountCalculationService.calculateEffectivePrice(unit);

        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(ci -> ci.getItemUnit().getId().equals(unit.getId()))
                .findFirst();

        if (existingItemOpt.isPresent()) {
            CartItem existing = existingItemOpt.get();
            int newQty = existing.getQuantity() + request.getQuantity();
            if (unit.getStockQuantity() < newQty) {
                throw new IllegalStateException("Cannot add more. Not enough stock available for item unit: " + unit.getItem().getName() + " - " + unit.getLabel());
            }
            existing.setQuantity(newQty);
            // Update snapshot price to the latest on add
            existing.setPriceSnapshot(currentEffectivePrice);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setItemUnit(unit);
            newItem.setShop(shopRepository.findById(shopId).orElseThrow());
            newItem.setQuantity(request.getQuantity());
            newItem.setPriceSnapshot(currentEffectivePrice);
            cart.getItems().add(newItem);
        }

        cartRepository.save(cart);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto updateQuantity(Long shopId, String sessionId, Long cartItemId, int quantity) {
        Cart cart = getOrCreateCart(shopId, sessionId);
        CartItem cartItem = cart.getItems().stream()
                .filter(ci -> ci.getId().equals(cartItemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Cart item not found"));

        if (cartItem.getItemUnit().getStockQuantity() < quantity) {
            throw new IllegalStateException("Not enough stock available for item unit: " + cartItem.getItemUnit().getItem().getName() + " - " + cartItem.getItemUnit().getLabel());
        }

        cartItem.setQuantity(quantity);
        cartRepository.save(cart);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto removeCartItem(Long shopId, String sessionId, Long cartItemId) {
        Cart cart = getOrCreateCart(shopId, sessionId);
        cart.getItems().removeIf(ci -> ci.getId().equals(cartItemId));
        cartRepository.save(cart);
        return mapToDto(cart);
    }

    @Transactional
    public CartDto clearCart(Long shopId, String sessionId) {
        Cart cart = getOrCreateCart(shopId, sessionId);
        cart.getItems().clear();
        cartRepository.save(cart);
        return mapToDto(cart);
    }
    
    public Cart getOrCreateCart(Long shopId, String sessionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            authenticatedUserId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        }

        Shop shop = shopRepository.findById(shopId).orElseThrow(() -> new IllegalArgumentException("Shop not found"));

        if (authenticatedUserId != null) {
            final Long uid = authenticatedUserId; // effectively final
            return cartRepository.findFirstByShopIdAndUserIdOrderByIdDesc(shopId, uid).orElseGet(() -> {
                Cart newCart = new Cart();
                newCart.setSessionId(sessionId != null ? sessionId : java.util.UUID.randomUUID().toString());
                newCart.setUserId(uid);
                newCart.setShop(shop);
                return cartRepository.save(newCart);
            });
        }

        return cartRepository.findByShopIdAndSessionId(shopId, sessionId).orElseGet(() -> {
            Cart newCart = new Cart();
            newCart.setSessionId(sessionId != null && !sessionId.isBlank() ? sessionId : java.util.UUID.randomUUID().toString());
            newCart.setShop(shop);
            return cartRepository.save(newCart);
        });
    }

    @Transactional
    public void mergeCart(Long shopId, String sessionId, Long userId) {
        if (sessionId == null || shopId == null) return;

        Optional<Cart> guestCartOpt = cartRepository.findByShopIdAndSessionId(shopId, sessionId);
        Optional<Cart> userCartOpt = cartRepository.findFirstByShopIdAndUserIdOrderByIdDesc(shopId, userId);

        if (guestCartOpt.isEmpty()) {
            // No guest cart to merge, but ensure user has a cart
            if (userCartOpt.isEmpty()) {
                Shop shop = shopRepository.findById(shopId).orElseThrow();
                Cart newCart = new Cart();
                newCart.setSessionId(java.util.UUID.randomUUID().toString());
                newCart.setUserId(userId);
                newCart.setShop(shop);
                cartRepository.save(newCart);
            }
            return;
        }

        Cart guestCart = guestCartOpt.get();

        if (userCartOpt.isPresent()) {
            Cart userCart = userCartOpt.get();
            // If they are the same cart, do nothing
            if (userCart.getId().equals(guestCart.getId())) {
                return;
            }

            // Merge items from guest cart to user cart
            for (CartItem guestItem : guestCart.getItems()) {
                Optional<CartItem> existingItemOpt = userCart.getItems().stream()
                        .filter(ci -> ci.getItemUnit().getId().equals(guestItem.getItemUnit().getId()))
                        .findFirst();

                if (existingItemOpt.isPresent()) {
                    CartItem existingItem = existingItemOpt.get();
                    // Add quantity, capping at stock
                    int newQty = existingItem.getQuantity() + guestItem.getQuantity();
                    if (newQty > existingItem.getItemUnit().getStockQuantity()) {
                        newQty = existingItem.getItemUnit().getStockQuantity();
                    }
                    existingItem.setQuantity(newQty);
                } else {
                    CartItem newItem = new CartItem();
                    newItem.setCart(userCart);
                    newItem.setShop(shopRepository.findById(shopId).orElseThrow());
                    newItem.setItemUnit(guestItem.getItemUnit());
                    newItem.setQuantity(guestItem.getQuantity());
                    newItem.setPriceSnapshot(guestItem.getPriceSnapshot());
                    userCart.getItems().add(newItem);
                }
            }
            cartRepository.save(userCart);
            cartRepository.delete(guestCart);
        } else {
            // User doesn't have a cart, claim the guest cart
            guestCart.setUserId(userId);
            cartRepository.save(guestCart);
        }
    }

    private CartDto mapToDto(Cart cart) {
        CartDto dto = new CartDto();
        dto.setId(cart.getId());
        dto.setSessionId(cart.getSessionId());

        BigDecimal subtotal = BigDecimal.ZERO;
        
        List<CartItemDto> itemDtos = cart.getItems().stream().map(ci -> {
            CartItemDto ciDto = new CartItemDto();
            ciDto.setId(ci.getId());
            ciDto.setItemId(ci.getItemUnit().getItem().getId());
            ciDto.setItemUnitId(ci.getItemUnit().getId());
            ciDto.setItemName(ci.getItemUnit().getItem().getName());
            ciDto.setUnitLabel(ci.getItemUnit().getLabel());
            ciDto.setUnitDescription(ci.getItemUnit().getDescription());
            ciDto.setImageUrl(ci.getItemUnit().getItem().getImageUrl());
            ciDto.setQuantity(ci.getQuantity());
            ciDto.setUnitPrice(ci.getPriceSnapshot());
            
            BigDecimal lineTotal = ci.getPriceSnapshot().multiply(BigDecimal.valueOf(ci.getQuantity()));
            ciDto.setLineTotal(lineTotal);
            
            return ciDto;
        }).collect(Collectors.toList());
        
        for (CartItemDto itemDto : itemDtos) {
            subtotal = subtotal.add(itemDto.getLineTotal());
        }
        
        dto.setItems(itemDtos);
        dto.setSubtotal(subtotal);
        
        dto.setTax(BigDecimal.ZERO);
        dto.setShipping(BigDecimal.ZERO);
        dto.setTotal(subtotal);

        return dto;
    }
}
