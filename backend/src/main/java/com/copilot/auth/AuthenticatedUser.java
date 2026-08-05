package com.copilot.auth;

import java.util.UUID;

/** What ends up in SecurityContext for every authenticated request — just enough
 *  (id + role) for controllers/services to do ownership checks without a DB hit. */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
