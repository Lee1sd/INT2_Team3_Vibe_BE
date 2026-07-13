package com.careerdungeon.global.exception;

public record ErrorResponse(String code, String message, int status) {}