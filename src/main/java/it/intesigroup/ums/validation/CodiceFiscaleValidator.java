package it.intesigroup.ums.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodiceFiscaleValidator implements ConstraintValidator<CodiceFiscale, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        String cf = value.trim().toUpperCase();
        if (!cf.matches("^[A-Z0-9]{16}$")) return false;
        return verifyChecksum(cf);
    }

    private boolean verifyChecksum(String cf) {
        int[] evenMap = new int[256];
        for (char c = 0; c < 256; c++) evenMap[c] = Character.isDigit(c) ? c - '0' : c - 'A';
        int[] oddMap = new int[256];
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int[] weights = {
                1,0,5,7,9,13,15,17,19,21,
                1,0,5,7,9,13,15,17,19,21,
                2,4,18,20,11,3,6,8,12,14,16,10,22,25,24,23
        };
        for (int i = 0; i < chars.length(); i++) {
            oddMap[chars.charAt(i)] = weights[i];
        }
        int sum = 0;
        for (int i = 0; i < 15; i++) {
            char c = cf.charAt(i);
            sum += ((i % 2) == 0) ? oddMap[c] : evenMap[c];
        }
        char expected = (char) ('A' + (sum % 26));
        return expected == cf.charAt(15);
    }
}
