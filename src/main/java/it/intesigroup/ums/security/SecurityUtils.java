package it.intesigroup.ums.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

public class SecurityUtils {
    public static boolean hasAnyRole(String... roles) {
        Set<String> userRoles = currentRoles();
        for (String r : roles) {
            if (userRoles.contains(r)) return true;
        }
        return false;
    }

    public static Set<String> currentRoles() {
        Set<String> set = new HashSet<>();
        Authentication auth = SecurityContextHolder.getContext() != null ? SecurityContextHolder.getContext().getAuthentication() : null;
        if (auth == null) return set;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String au = a.getAuthority();
            if (au != null && au.startsWith("ROLE_")) {
                set.add(au.substring(5));
            }
        }
        return set;
    }
}
