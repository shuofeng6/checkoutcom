package com.checkout.payment.gateway.exception;

import org.springframework.http.HttpStatus;

public class BankException extends RuntimeException {
  private final HttpStatus httpStatus;

  public BankException(String message, HttpStatus httpStatus, Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }
}
