package it.intesigroup.ums.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = CodiceFiscaleValidator.class)
@Target({FIELD})
@Retention(RUNTIME)
public @interface CodiceFiscale {
    String message() default "codice fiscale non valido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
