package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.exception.BankException;
import com.checkout.payment.gateway.model.BankSimulatorRequest;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    BankSimulatorRequest bankRequest = BankSimulatorRequest.from(paymentRequest);

    try {
      return restTemplate.postForObject(
          bankUrl,
          bankRequest,
          BankSimulatorResponse.class
      );
    } catch (HttpClientErrorException e) {
      LOG.error("Bank rejected request with {}: {}", e.getStatusCode(), e.getMessage());
      throw new BankException("Bank rejected request: " + e.getMessage(), 
          HttpStatus.BAD_GATEWAY, e);

    } catch (HttpServerErrorException e) {
      LOG.error("Bank service error with {}: {}", e.getStatusCode(), e.getMessage());
      HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
      throw new BankException("Bank service error: " + e.getStatusText(),
          status != null ? status : HttpStatus.SERVICE_UNAVAILABLE, e);

    } catch (Exception e) {
      LOG.error("Unexpected error calling bank: {}", e.getMessage());
      throw new BankException("Error communicating with bank", 
          HttpStatus.INTERNAL_SERVER_ERROR, e);
    }
  }
}
