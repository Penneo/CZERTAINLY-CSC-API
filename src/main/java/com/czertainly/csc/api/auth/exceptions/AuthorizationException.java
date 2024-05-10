package com.czertainly.csc.api.auth.exceptions;

import com.czertainly.csc.common.exceptions.ApplicationException;

public class AuthorizationException extends ApplicationException {
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
