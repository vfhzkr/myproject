package com.example.myproject.exception;

import com.example.myproject.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器，确保所有异常返回统一的 Result 格式
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNotFound(NoResourceFoundException e) {
        // favicon.ico 等静态资源 404 无需记录错误日志
        return Result.fail("资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统繁忙，请稍后再试");
    }
}
