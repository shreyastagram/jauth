package com.fixhomi.auth.entity;

/**
 * User roles supported by FixHomi authentication service.
 * These roles are included in JWT tokens for authorization.
 */
public enum Role {
    /**
     * Regular customer/end-user
     */
    USER,
    
    /**
     * Service provider (plumber, electrician, etc.)
     */
    SERVICE_PROVIDER,
    
    /**
     * Administrative user with full access
     */
    ADMIN,
    
    /**
     * Customer support staff
     */
    SUPPORT,
    
    /**
     * IT administrator for system management
     */
    IT_ADMIN
}
