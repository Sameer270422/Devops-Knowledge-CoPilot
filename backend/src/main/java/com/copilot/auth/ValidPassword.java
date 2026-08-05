package com.copilot.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Kept in sync with the frontend's live checklist (frontend/src/utils/validation.ts) —
 * both enforce the same four rules, but this is the actual enforcement point. The
 * frontend check is only there so the user sees feedback while typing instead of
 * discovering a rejected password after submitting.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
@Documented
public @interface ValidPassword {
    String message() default "Password must be at least 10 characters and include an uppercase letter, a number, and a symbol";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
