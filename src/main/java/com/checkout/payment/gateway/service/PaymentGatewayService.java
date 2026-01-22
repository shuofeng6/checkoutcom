package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validator.PaymentValidator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankSimulatorService bankSimulatorService;
  private final PaymentValidator paymentValidator;

  public PaymentGatewayService(
      PaymentsRepository paymentsRepository,
      BankSimulatorService bankSimulatorService,
      PaymentValidator paymentValidator) {
    this.paymentsRepository = paymentsRepository;
    this.bankSimulatorService = bankSimulatorService;
    this.paymentValidator = paymentValidator;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Payment not found"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    LOG.info("Processing payment request for card ending in {}",
        paymentRequest.getCardNumber() != null && paymentRequest.getCardNumber().length() >= 4
            ? paymentRequest.getCardNumber().substring(paymentRequest.getCardNumber().length() - 4)
            : "****");

    // 1. Validate the request - if validation fails, create REJECTED payment
    try {
      paymentValidator.validate(paymentRequest);
    } catch (Exception e) {
      LOG.warn("Payment validation failed: {}", e.getMessage());
      PostPaymentResponse rejectedResponse = buildRejectedResponse(paymentRequest);
      paymentsRepository.add(rejectedResponse);
      return rejectedResponse;
    }

    // 2. Call bank simulator (if bank call fails, exception is thrown - payment not stored)
    BankSimulatorResponse bankResponse = bankSimulatorService.authorize(paymentRequest);

    // 3. Build and store the response (only reached if validation passed and bank call succeeded)
    PostPaymentResponse response = buildResponse(paymentRequest, bankResponse);
    paymentsRepository.add(response);

    LOG.info("Payment processed successfully with ID {} and status {}",
        response.getId(), response.getStatus());

    return response;
  }

  private PostPaymentResponse buildResponse(
      PostPaymentRequest request,
      BankSimulatorResponse bankResponse) {

    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());

    // Set status based on bank response
    response.setStatus(bankResponse.isAuthorized()
        ? PaymentStatus.AUTHORIZED
        : PaymentStatus.DECLINED);

    // Extract last 4 digits of card number
    String cardNumber = request.getCardNumber();
    response.setCardNumberLastFour(
        Integer.parseInt(cardNumber.substring(cardNumber.length() - 4))
    );

    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

    return response;
  }

  private PostPaymentResponse buildRejectedResponse(PostPaymentRequest request) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(UUID.randomUUID());
    response.setStatus(PaymentStatus.REJECTED);

    // Try to extract card details if available
    if (request.getCardNumber() != null && request.getCardNumber().length() >= 4) {
      String cardNumber = request.getCardNumber();
      try {
        response.setCardNumberLastFour(
            Integer.parseInt(cardNumber.substring(cardNumber.length() - 4))
        );
      } catch (NumberFormatException e) {
        response.setCardNumberLastFour(0);
      }
    } else {
      response.setCardNumberLastFour(0);
    }

    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());

    return response;
  }
}
