package com.tms.thesissystem.application.service.security;

import com.tms.thesissystem.application.service.AuthAccountStore;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@NullMarked
public class AuthAccountUserDetailsService implements UserDetailsService {
    private final AuthAccountStore authAccountStore;

    @Override
    public AuthenticatedAccount loadUserByUsername(String username) throws UsernameNotFoundException {
        return authAccountStore.findByUsername(username)
                .map(AuthenticatedAccount::new)
                .orElseThrow(() -> new UsernameNotFoundException("Хэрэглэгч олдсонгүй."));
    }
}
