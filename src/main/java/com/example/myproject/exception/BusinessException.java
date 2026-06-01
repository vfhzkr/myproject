package com.example.myproject.exception;

/**
 * 业务异常，由 GlobalExceptionHandler 统一捕获并返回 Result.fail()
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
