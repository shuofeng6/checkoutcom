package com.checkout.payment.gateway.validator;

import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PaymentValidator {

  private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "GBP", "EUR");

  public void validate(PostPaymentRequest request) {
    List<String> errors = new ArrayList<>();

    // Validate card number
    if (request.getCardNumber() == null || request.getCardNumber().isEmpty()) {
      errors.add("Card number is required");
    } else if (request.getCardNumber().length() < 14 || request.getCardNumber().length() > 19) {
      errors.add("Card number must be between 14-19 characters");
    } else if (!request.getCardNumber().chars().allMatch(Character::isDigit)) {
      errors.add("Card number must contain only numeric characters");
    }

    // Validate expiry month
    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      errors.add("Expiry month must be between 1-12");
    }

    // Validate expiry year and that date is in future
    int currentYear = Year.now().getValue();
    if (request.getExpiryYear() < currentYear) {
      errors.add("Expiry year must be in the future");
    } else if (request.getExpiryMonth() >= 1 && request.getExpiryMonth() <= 12) {
      // Only check month+year combination if month is valid
      YearMonth cardExpiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      YearMonth now = YearMonth.now();
      if (cardExpiry.isBefore(now)) {
        errors.add("Card expiry date must be in the future");
      }
    }

    // Validate currency
    if (request.getCurrency() == null || request.getCurrency().length() != 3) {
      errors.add("Currency must be exactly 3 characters");
    } else if (!SUPPORTED_CURRENCIES.contains(request.getCurrency().toUpperCase())) {
      errors.add("Currency must be one of: USD, GBP, EUR");
    }

    // Validate amount
    if (request.getAmount() <= 0) {
      errors.add("Amount must be a positive integer");
    }

    // Validate CVV
    String cvv = String.valueOf(request.getCvv());
    if (cvv.length() < 3 || cvv.length() > 4) {
      errors.add("CVV must be 3-4 characters");
    } else if (!cvv.chars().allMatch(Character::isDigit)) {
      errors.add("CVV must contain only numeric characters");
    }

    // If any errors, throw exception
    if (!errors.isEmpty()) {
      throw new PaymentValidationException(String.join(", ", errors));
    }
  }
}
