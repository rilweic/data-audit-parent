package com.github.thestyleofme.comparison.common.infra.exceptions;

/**
 * <p>
 * description
 * </p>
 *
 * @author isaac 2020/10/22 15:18
 * @since 1.0.0
 */
public class HandlerException extends BaseException {

    private static final long serialVersionUID = 831033366052355257L;

    public HandlerException(String message) {
        super(message);
    }

    public HandlerException(String message, Object... params) {
        super(message, params);
    }

    public HandlerException(String message, Throwable cause) {
        super(message, cause);
    }

    public HandlerException(Throwable cause) {
        super(cause);
    }
}
