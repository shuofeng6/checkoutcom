package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.model.BankSimulatorRequest;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class BankSimulatorService {

  private static final Logger LOG = LoggerFactory.getLogger(BankSimulatorService.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public BankSimulatorService(
      RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public BankSimulatorResponse authorize(PostPaymentRequest paymentRequest) {
    // Transform: PostPaymentRequest → BankSimulatorRequest
    BankSimulatorRequest bankRequest = BankSimulatorRequest.from(paymentRequest);

    LOG.info("Calling bank simulator at {} for card ending in {}",
        bankUrl,
        paymentRequest.getCardNumber().substring(paymentRequest.getCardNumber().length() - 4));

    try {
      // Call bank API
      BankSimulatorResponse response = restTemplate.postForObject(
          bankUrl,
          bankRequest,
          BankSimulatorResponse.class
      );

      LOG.info("Bank response: authorized={}", response != null && response.isAuthorized());
      return response;

    } catch (HttpClientErrorException e) {
      // Handle 400 Bad Request
      LOG.error("Bank rejected request with 400: {}", e.getMessage());
      throw new BankException("Bank rejected request: " + e.getMessage(), e);

    } catch (HttpServerErrorException e) {
      // Handle 503 Service Unavailable
      LOG.error("Bank service unavailable with {}: {}", e.getStatusCode(), e.getMessage());
      throw new BankException("Bank service unavailable", e);

    } catch (Exception e) {
      LOG.error("Unexpected error calling bank: {}", e.getMessage());
      throw new BankException("Error communicating with bank", e);
    }
  }
}
