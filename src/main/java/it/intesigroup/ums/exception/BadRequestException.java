package it.intesigroup.ums.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
