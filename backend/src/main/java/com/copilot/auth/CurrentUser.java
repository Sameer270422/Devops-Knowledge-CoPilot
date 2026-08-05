package com.copilot.auth;

import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper so controllers don't repeat the SecurityContext cast everywhere. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthenticatedUser get() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
