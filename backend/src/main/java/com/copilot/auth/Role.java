package com.copilot.auth;

/** USER can only touch their own resources. ADMIN is the instance owner (Phase 1: still
 *  single-tenant, but this is the seam Phase 2's OWNER/ADMIN/MEMBER roles extend). */
public enum Role {
    USER, ADMIN
}
