package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.exception.PaymentValidationException;
import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validator.PaymentValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock
  private PaymentsRepository paymentsRepository;

  @Mock
  private BankSimulatorService bankSimulatorService;

  @Mock
  private PaymentValidator paymentValidator;

  @InjectMocks
  private PaymentGatewayService paymentGatewayService;

  private PostPaymentRequest validRequest;

  @BeforeEach
  void setUp() {
    validRequest = new PostPaymentRequest();
    validRequest.setCardNumber("2222405343248877");
    validRequest.setExpiryMonth(12);
    validRequest.setExpiryYear(2027);
    validRequest.setCurrency("GBP");
    validRequest.setAmount(1000);
    validRequest.setCvv(123);
  }

  @Test
  void shouldReturnAuthorizedWhenBankApproves() {
    // Given
    doNothing().when(paymentValidator).validate(any());
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-123");
    when(bankSimulatorService.authorize(validRequest)).thenReturn(bankResponse);

    // When
    PostPaymentResponse response = paymentGatewayService.processPayment(validRequest);

    // Then
    assertNotNull(response);
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());
    assertEquals(8877, response.getCardNumberLastFour());
    assertEquals(12, response.getExpiryMonth());
    assertEquals(2027, response.getExpiryYear());
    assertEquals("GBP", response.getCurrency());
    assertEquals(1000, response.getAmount());
    assertNotNull(response.getId());

    verify(paymentValidator).validate(validRequest);
    verify(bankSimulatorService).authorize(validRequest);
    verify(paymentsRepository).add(response);
  }

  @Test
  void shouldReturnDeclinedWhenBankDeclines() {
    // Given
    doNothing().when(paymentValidator).validate(any());
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(false, null);
    when(bankSimulatorService.authorize(validRequest)).thenReturn(bankResponse);

    // When
    PostPaymentResponse response = paymentGatewayService.processPayment(validRequest);

    // Then
    assertNotNull(response);
    assertEquals(PaymentStatus.DECLINED, response.getStatus());
    assertEquals(8877, response.getCardNumberLastFour());

    verify(paymentValidator).validate(validRequest);
    verify(bankSimulatorService).authorize(validRequest);
    verify(paymentsRepository).add(response);
  }

  @Test
  void shouldThrowExceptionWhenValidationFails() {
    // Given
    doThrow(new PaymentValidationException("Invalid card number"))
        .when(paymentValidator).validate(validRequest);

    // When / Then
    assertThrows(PaymentValidationException.class,
        () -> paymentGatewayService.processPayment(validRequest));

    verify(paymentValidator).validate(validRequest);
    verify(bankSimulatorService, never()).authorize(any());
    verify(paymentsRepository, never()).add(any());
  }

  @Test
  void shouldExtractLastFourDigitsCorrectly() {
    // Given
    doNothing().when(paymentValidator).validate(any());
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-123");
    when(bankSimulatorService.authorize(validRequest)).thenReturn(bankResponse);

    // When
    PostPaymentResponse response = paymentGatewayService.processPayment(validRequest);

    // Then
    assertEquals(8877, response.getCardNumberLastFour());
  }

  @Test
  void shouldGenerateUniquePaymentId() {
    // Given
    doNothing().when(paymentValidator).validate(any());
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-123");
    when(bankSimulatorService.authorize(validRequest)).thenReturn(bankResponse);

    // When
    PostPaymentResponse response = paymentGatewayService.processPayment(validRequest);

    // Then
    assertNotNull(response.getId());
  }

  @Test
  void shouldStorePaymentInRepository() {
    // Given
    doNothing().when(paymentValidator).validate(any());
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-123");
    when(bankSimulatorService.authorize(validRequest)).thenReturn(bankResponse);

    // When
    paymentGatewayService.processPayment(validRequest);

    // Then
    ArgumentCaptor<PostPaymentResponse> captor = ArgumentCaptor.forClass(PostPaymentResponse.class);
    verify(paymentsRepository).add(captor.capture());

    PostPaymentResponse storedPayment = captor.getValue();
    assertNotNull(storedPayment.getId());
    assertEquals(PaymentStatus.AUTHORIZED, storedPayment.getStatus());
  }

  @Test
  void shouldRetrievePaymentById() {
    // Given
    UUID paymentId = UUID.randomUUID();
    PostPaymentResponse expectedResponse = new PostPaymentResponse();
    expectedResponse.setId(paymentId);
    expectedResponse.setStatus(PaymentStatus.AUTHORIZED);

    when(paymentsRepository.get(paymentId)).thenReturn(Optional.of(expectedResponse));

    // When
    PostPaymentResponse response = paymentGatewayService.getPaymentById(paymentId);

    // Then
    assertNotNull(response);
    assertEquals(paymentId, response.getId());
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());

    verify(paymentsRepository).get(paymentId);
  }

  @Test
  void shouldThrowExceptionWhenPaymentNotFound() {
    // Given
    UUID nonExistentId = UUID.randomUUID();
    when(paymentsRepository.get(nonExistentId)).thenReturn(Optional.empty());

    // When / Then
    assertThrows(EventProcessingException.class,
        () -> paymentGatewayService.getPaymentById(nonExistentId));

    verify(paymentsRepository).get(nonExistentId);
  }
}
