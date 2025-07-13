package com.simon.springboot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * SpringBoot全局异常处理
 *
 * @author Simon Cai
 * @version 1.0
 * @since 2025-07-13
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandle {

    private AiAnalyzeHandler aiAnalyzeHandler;

    public GlobalExceptionHandle(AiAnalyzeHandler aiAnalyzeHandler) {
        this.aiAnalyzeHandler = aiAnalyzeHandler;
    }

    @ExceptionHandler(Exception.class)
    public String handleAllExceptions(Exception ex) {
        aiAnalyzeHandler.handle(ex);
        return "error";
    }
}
