package com.ecommerce.backend.service;

import com.ecommerce.backend.dto.*;
import com.ecommerce.backend.entity.*;
import com.ecommerce.backend.repository.CartRepository;
import com.ecommerce.backend.repository.ItemUnitRepository;
import com.ecommerce.backend.repository.OrderRepository;
import com.ecommerce.backend.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ecommerce.backend.security.UserDetailsImpl;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final PaymentService paymentService;
    private final ShopRepository shopRepository;
    private final com.ecommerce.backend.repository.NotificationRepository notificationRepository;

    @Transactional
    public OrderDto checkout(Long shopId, String sessionId, CheckoutRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            authenticatedUserId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        }

        Cart cart;
        if (authenticatedUserId != null) {
            cart = cartRepository.findFirstByShopIdAndUserIdOrderByIdDesc(shopId, authenticatedUserId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        } else {
            cart = cartRepository.findByShopIdAndSessionId(shopId, sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Cart not found"));
        }

        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty cart");
        }

        // 1. Process payment (Simulated)
        paymentService.processPayment(request.getPaymentMethod(), request.getCardNumber(), request.getCardExpiry(), request.getCardCvv());

        // 2. Validate stock and deduct
        for (CartItem cartItem : cart.getItems()) {
            ItemUnit unit = cartItem.getItemUnit();
            if (!unit.getShop().getId().equals(shopId)) {
                throw new IllegalStateException("Cart contains items from another shop.");
            }
            if (unit.getStockQuantity() < cartItem.getQuantity()) {
                throw new IllegalStateException("Not enough stock for item unit: " + unit.getItem().getName() + " - " + unit.getLabel());
            }
            unit.setStockQuantity(unit.getStockQuantity() - cartItem.getQuantity());
            itemUnitRepository.save(unit); // Transactional flush
        }

        Shop shop = shopRepository.findById(shopId).orElseThrow();

        // 3. Create Order
        Order order = new Order();
        String effectiveSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : (authenticatedUserId != null ? "user-" + authenticatedUserId : java.util.UUID.randomUUID().toString());
        order.setSessionId(effectiveSessionId);
        order.setUserId(authenticatedUserId);
        order.setShop(shop);
        order.setStatus(OrderStatus.PLACED);
        order.setCreatedAt(LocalDateTime.now());
        order.setDeliveryOption(request.getDeliveryOption());
        order.setPaymentMethod(request.getPaymentMethod());
        
        if (request.getPaymentMethod() == PaymentMethod.CARD) {
            order.setMaskedCardNumber(paymentService.maskCardNumber(request.getCardNumber()));
        }
        
        order.setPaymentProofUrl(request.getPaymentProofUrl());
        order.setTransactionCode(request.getTransactionCode());
        Address address = new Address();
        address.setFullName(request.getShippingAddress().getFullName());
        address.setPhone(request.getShippingAddress().getPhone());
        address.setAddressLine1(request.getShippingAddress().getAddressLine1());
        address.setAddressLine2(request.getShippingAddress().getAddressLine2());
        address.setCity(request.getShippingAddress().getCity());
        address.setPostalCode(request.getShippingAddress().getPostalCode());
        order.setShippingAddress(address);

        // 4. Calculate totals
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setItemId(cartItem.getItemUnit().getItem().getId());
            orderItem.setItemUnitId(cartItem.getItemUnit().getId());
            orderItem.setItemName(cartItem.getItemUnit().getItem().getName());
            orderItem.setUnitLabel(cartItem.getItemUnit().getLabel());
            orderItem.setUnitDescription(cartItem.getItemUnit().getDescription());
            orderItem.setImageUrl(cartItem.getItemUnit().getItem().getImageUrl());
            orderItem.setUnitPrice(cartItem.getPriceSnapshot());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setShop(shop);
            
            order.getItems().add(orderItem);
            
            BigDecimal lineTotal = orderItem.getUnitPrice().multiply(BigDecimal.valueOf(orderItem.getQuantity()));
            subtotal = subtotal.add(lineTotal);
        }

        order.setSubtotal(subtotal);

        order.setTotal(subtotal);

        // 5. Save order
        Order savedOrder = orderRepository.save(order);

        // 6. Clear cart
        cart.getItems().clear();
        cartRepository.save(cart);

        return mapToDto(savedOrder);
    }

    public List<OrderDto> getOrdersForSession(Long shopId, String sessionId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            authenticatedUserId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        }

        List<Order> orders;
        if (authenticatedUserId != null) {
            orders = orderRepository.findByShopIdAndUserIdOrderByCreatedAtDesc(shopId, authenticatedUserId);
        } else {
            orders = orderRepository.findByShopIdAndSessionIdOrderByCreatedAtDesc(shopId, sessionId);
        }

        return orders.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public OrderDto getOrderById(Long shopId, Long id, String sessionId) {
        Order order = orderRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new com.ecommerce.backend.exception.ResourceNotFoundException("Order not found with id: " + id));
                
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = null;
        boolean isAdmin = false;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            UserDetailsImpl userDetails = (UserDetailsImpl) auth.getPrincipal();
            authenticatedUserId = userDetails.getId();
            isAdmin = userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SHOP_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        }

        if (!isAdmin) {
            if (authenticatedUserId != null) {
                if (!authenticatedUserId.equals(order.getUserId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Access Denied: You do not own this order.");
                }
            } else {
                if (sessionId == null || !sessionId.equals(order.getSessionId()) || order.getUserId() != null) {
                    throw new org.springframework.security.access.AccessDeniedException("Access Denied: You do not own this order.");
                }
            }
        }
        
        return mapToDto(order);
    }
    
    // For admin
    public org.springframework.data.domain.Page<OrderDto> getAllOrders(Long shopId, int page, int size, OrderStatus status, String search) {
        if (search != null && search.trim().isEmpty()) search = null;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Order> orders = orderRepository.findByShopIdAndStatusAndSearch(shopId, status, search, pageable);
        return orders.map(this::mapToDto);
    }

    @Transactional
    public OrderDto cancelOrder(Long shopId, Long id, String sessionId) {
        Order order = orderRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new com.ecommerce.backend.exception.ResourceNotFoundException("Order not found with id: " + id));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long authenticatedUserId = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            authenticatedUserId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        }

        if (authenticatedUserId != null) {
            if (!authenticatedUserId.equals(order.getUserId())) {
                throw new org.springframework.security.access.AccessDeniedException("Access Denied: You do not own this order.");
            }
        } else {
            if (sessionId == null || !sessionId.equals(order.getSessionId()) || order.getUserId() != null) {
                throw new org.springframework.security.access.AccessDeniedException("Access Denied: You do not own this order.");
            }
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException("Order cannot be cancelled in its current state.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        
        for (OrderItem item : order.getItems()) {
            ItemUnit unit = itemUnitRepository.findById(item.getItemUnitId()).orElse(null);
            if (unit != null) {
                unit.setStockQuantity(unit.getStockQuantity() + item.getQuantity());
                itemUnitRepository.save(unit);
            }
        }

        return mapToDto(order);
    }
    
    @Transactional
    public OrderDto updateOrderStatus(Long shopId, Long id, OrderStatus status) {
        Order order = orderRepository.findByIdAndShopId(id, shopId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        order.setStatus(status);
        order = orderRepository.save(order);
        
        if (order.getUserId() != null) {
            com.ecommerce.backend.entity.Notification notif = new com.ecommerce.backend.entity.Notification();
            notif.setShop(order.getShop());
            notif.setUserId(order.getUserId());
            notif.setTitle("Order Status Updated");
            notif.setMessage("Your order #" + order.getId() + " is now " + status.name());
            notificationRepository.save(notif);
        }
        return mapToDto(order);
    }

    private OrderDto mapToDto(Order order) {
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setStatus(order.getStatus());
        dto.setDeliveryOption(order.getDeliveryOption());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setSubtotal(order.getSubtotal());

        dto.setTotal(order.getTotal());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setMaskedCardNumber(order.getMaskedCardNumber());
        dto.setPaymentProofUrl(order.getPaymentProofUrl());
        dto.setTransactionCode(order.getTransactionCode());

        AddressDto addrDto = new AddressDto();
        addrDto.setFullName(order.getShippingAddress().getFullName());
        addrDto.setPhone(order.getShippingAddress().getPhone());
        addrDto.setAddressLine1(order.getShippingAddress().getAddressLine1());
        addrDto.setAddressLine2(order.getShippingAddress().getAddressLine2());
        addrDto.setCity(order.getShippingAddress().getCity());
        addrDto.setPostalCode(order.getShippingAddress().getPostalCode());
        dto.setShippingAddress(addrDto);

        List<OrderItemDto> itemDtos = order.getItems().stream().map(oi -> {
            OrderItemDto oiDto = new OrderItemDto();
            oiDto.setId(oi.getId());
            oiDto.setItemId(oi.getItemId());
            oiDto.setItemUnitId(oi.getItemUnitId());
            oiDto.setItemName(oi.getItemName());
            oiDto.setUnitLabel(oi.getUnitLabel());
            oiDto.setUnitDescription(oi.getUnitDescription());
            oiDto.setImageUrl(oi.getImageUrl());
            oiDto.setUnitPrice(oi.getUnitPrice());
            oiDto.setQuantity(oi.getQuantity());
            return oiDto;
        }).collect(Collectors.toList());
        dto.setItems(itemDtos);

        return dto;
    }
}
