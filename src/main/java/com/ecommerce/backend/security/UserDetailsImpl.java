package com.ecommerce.backend.security;

import com.ecommerce.backend.entity.User;
import com.ecommerce.backend.entity.ShopStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;

public class UserDetailsImpl implements UserDetails {
    private final Long id;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final Long shopId;
    private final boolean enabled;

    public UserDetailsImpl(Long id, String email, String password, Collection<? extends GrantedAuthority> authorities, Long shopId, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
        this.shopId = shopId;
        this.enabled = enabled;
    }

    public static UserDetailsImpl build(User user) {
        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        boolean isEnabled = true;
        if (user.getRole() == com.ecommerce.backend.entity.Role.SHOP_ADMIN && user.getShop() != null) {
            isEnabled = (user.getShop().getStatus() == ShopStatus.ACTIVE);
        }
        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Collections.singletonList(authority),
                user.getShop() != null ? user.getShop().getId() : null,
                isEnabled
        );
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
