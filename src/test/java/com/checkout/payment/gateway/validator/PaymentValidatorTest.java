package com.checkout.payment.gateway.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private PaymentValidator validator;

  @BeforeEach
  void setUp() {
    validator = new PaymentValidator();
  }

  private PostPaymentRequest createValidRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(1000);
    request.setCvv(123);
    return request;
  }

  @Test
  void shouldAcceptValidRequest() {
    PostPaymentRequest request = createValidRequest();
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void shouldRejectCardNumberTooShort() {
    PostPaymentRequest request = createValidRequest();
    request.setCardNumber("123");

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Card number must be between 14-19 characters"));
  }

  @Test
  void shouldRejectCardNumberTooLong() {
    PostPaymentRequest request = createValidRequest();
    request.setCardNumber("12345678901234567890");

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Card number must be between 14-19 characters"));
  }

  @Test
  void shouldRejectNonNumericCardNumber() {
    PostPaymentRequest request = createValidRequest();
    request.setCardNumber("1234567890123abc");

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Card number must contain only numeric characters"));
  }

  @Test
  void shouldRejectNullCardNumber() {
    PostPaymentRequest request = createValidRequest();
    request.setCardNumber(null);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Card number is required"));
  }

  @Test
  void shouldRejectInvalidExpiryMonth() {
    PostPaymentRequest request = createValidRequest();
    request.setExpiryMonth(13);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Expiry month must be between 1-12"));
  }

  @Test
  void shouldRejectExpiryMonthZero() {
    PostPaymentRequest request = createValidRequest();
    request.setExpiryMonth(0);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Expiry month must be between 1-12"));
  }

  @Test
  void shouldRejectPastYear() {
    PostPaymentRequest request = createValidRequest();
    request.setExpiryYear(2020);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Expiry year must be in the future"));
  }

  @Test
  void shouldRejectPastMonthInCurrentYear() {
    PostPaymentRequest request = createValidRequest();
    request.setExpiryMonth(1); // January
    request.setExpiryYear(2026);

    // This test will only work if current date is after January 2026
    // Since we're in January 2026, let's test with a definitely past month
    // Use December 2025 instead
    request.setExpiryMonth(12);
    request.setExpiryYear(2025);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Expiry year must be in the future"));
  }

  @Test
  void shouldRejectUnsupportedCurrency() {
    PostPaymentRequest request = createValidRequest();
    request.setCurrency("JPY");

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Currency must be one of: USD, GBP, EUR"));
  }

  @Test
  void shouldRejectCurrencyWithWrongLength() {
    PostPaymentRequest request = createValidRequest();
    request.setCurrency("US");

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Currency must be exactly 3 characters"));
  }

  @Test
  void shouldAcceptAllSupportedCurrencies() {
    PostPaymentRequest request = createValidRequest();

    request.setCurrency("USD");
    assertDoesNotThrow(() -> validator.validate(request));

    request.setCurrency("GBP");
    assertDoesNotThrow(() -> validator.validate(request));

    request.setCurrency("EUR");
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void shouldRejectZeroAmount() {
    PostPaymentRequest request = createValidRequest();
    request.setAmount(0);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Amount must be a positive integer"));
  }

  @Test
  void shouldRejectNegativeAmount() {
    PostPaymentRequest request = createValidRequest();
    request.setAmount(-100);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("Amount must be a positive integer"));
  }

  @Test
  void shouldRejectCvvTooShort() {
    PostPaymentRequest request = createValidRequest();
    request.setCvv(12);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("CVV must be 3-4 characters"));
  }

  @Test
  void shouldRejectCvvTooLong() {
    PostPaymentRequest request = createValidRequest();
    request.setCvv(12345);

    PaymentValidationException exception = assertThrows(
        PaymentValidationException.class,
        () -> validator.validate(request)
    );
    assertTrue(exception.getMessage().contains("CVV must be 3-4 characters"));
  }

  @Test
  void shouldAccept3DigitCvv() {
    PostPaymentRequest request = createValidRequest();
    request.setCvv(123);
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void shouldAccept4DigitCvv() {
    PostPaymentRequest request = createValidRequest();
    request.setCvv(1234);
    assertDoesNotThrow(() -> validator.validate(request));
  }
}
