package com.tms.thesissystem.application.service.security;

import com.tms.thesissystem.application.service.AuthAccountStore;
import com.tms.thesissystem.api.ApiDtos;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@NullMarked
public class AuthenticatedAccount implements UserDetails {
    private final AuthAccountStore.AuthAccount account;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedAccount(AuthAccountStore.AuthAccount account) {
        this.account = account;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole().toUpperCase(Locale.ROOT)));
    }

    public ApiDtos.AuthUserDto toAuthUser(Long resolvedUserId) {
        return new ApiDtos.AuthUserDto(
                resolvedUserId,
                account.username(),
                account.displayName() == null || account.displayName().isBlank() ? account.username() : account.displayName(),
                normalizedRole()
        );
    }

    public Long userId() {
        return account.userId();
    }

    public String normalizedRole() {
        return account.role() == null ? "student" : account.role().trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.passwordHash();
    }

    @Override
    public String getUsername() {
        return account.username();
    }
}
