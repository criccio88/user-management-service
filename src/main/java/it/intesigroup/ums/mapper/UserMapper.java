package it.intesigroup.ums.mapper;

import it.intesigroup.ums.domain.User;
import it.intesigroup.ums.dto.UserResponse;

public class UserMapper {
    public static UserResponse toResponse(User u) {
        return toResponse(u, false);
    }

    public static UserResponse toResponse(User u, boolean maskSensitive) {
        UserResponse res = new UserResponse();
        res.setId(u.getId());
        res.setUsername(u.getUsername());
        res.setEmail(maskSensitive ? maskEmail(u.getEmail()) : u.getEmail());
        res.setCodiceFiscale(maskSensitive ? maskCf(u.getCodiceFiscale()) : u.getCodiceFiscale());
        res.setNome(u.getNome());
        res.setCognome(u.getCognome());
        res.setStatus(u.getStatus());
        res.setCreatedAt(u.getCreatedAt());
        res.setUpdatedAt(u.getUpdatedAt());
        res.setRoles(u.getRoles());
        return res;
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String maskedLocal = local.length() <= 2 ? "*".repeat(local.length()) : local.charAt(0) + "*".repeat(local.length() - 2) + local.charAt(local.length() - 1);
        return maskedLocal + domain;
    }

    private static String maskCf(String cf) {
        if (cf == null || cf.length() < 6) return "********";
        return cf.substring(0, 3) + "********" + cf.substring(cf.length() - 3);
    }
}
