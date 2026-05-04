package com.tms.thesissystem.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiDtos.ApiErrorResponse handleDomainError(RuntimeException exception) {
        log.warn("Domain error: {}", exception.getMessage());
        return new ApiDtos.ApiErrorResponse(resolveMessage(exception, "Хүсэлтийг боловсруулах үед алдаа гарлаа."));
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiDtos.ApiErrorResponse handleNetworkError(IOException exception) {
        log.error("Downstream network error", exception);
        return new ApiDtos.ApiErrorResponse("Системтэй холбогдож чадсангүй. Дахин оролдоно уу.");
    }

    @ExceptionHandler(InterruptedException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiDtos.ApiErrorResponse handleInterrupted(InterruptedException exception) {
        Thread.currentThread().interrupt();
        log.error("Downstream call interrupted", exception);
        return new ApiDtos.ApiErrorResponse("Холболт тасалдлаа. Дахин оролдоно уу.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiDtos.ApiErrorResponse handleUnhandledError(Exception exception) {
        log.error("Unhandled error", exception);
        return new ApiDtos.ApiErrorResponse(resolveMessage(exception, "Дотоод алдаа гарлаа. Дараа дахин оролдоно уу."));
    }

    private String resolveMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallback;
        }
        return throwable.getMessage();
    }
}
