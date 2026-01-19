package com.checkout.payment.gateway.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-End tests with real BankSimulatorService.
 * These tests require the bank simulator to be running via docker-compose.
 * 
 * To run these tests:
 * 1. Start the bank simulator: docker-compose up
 * 2. Run the tests
 * 
 * These tests verify the complete integration including HTTP communication,
 * JSON serialization/deserialization, and the bank simulator's actual behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayE2ETest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void shouldProcessAuthorizedPaymentWithRealBank() throws Exception {
    // Card ending in odd number (7) -> Authorized
    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(1000));
  }

  @Test
  void shouldProcessDeclinedPaymentWithRealBank() throws Exception {
    // Card ending in even number (8) -> Declined
    String requestJson = """
        {
          "card_number": "2222405343248888",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "USD",
          "amount": 500,
          "cvv": 456
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8888));
  }

  @Test
  void shouldProcessMultiplePaymentsWithDifferentCardNumbers() throws Exception {
    // Test with card ending in 1 (odd) -> Authorized
    String request1 = """
        {
          "card_number": "4111111111111111",
          "expiry_month": 6,
          "expiry_year": 2027,
          "currency": "EUR",
          "amount": 250,
          "cvv": 999
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request1))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1111));

    // Test with card ending in 2 (even) -> Declined
    String request2 = """
        {
          "card_number": "4111111111111112",
          "expiry_month": 6,
          "expiry_year": 2027,
          "currency": "EUR",
          "amount": 250,
          "cvv": 999
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request2))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(1112));
  }

  @Test
  void shouldCreateAndRetrievePaymentWithRealBank() throws Exception {
    // Create payment with card ending in 3 (odd) -> Authorized
    String createRequest = """
        {
          "card_number": "5555555555554443",
          "expiry_month": 3,
          "expiry_year": 2028,
          "currency": "GBP",
          "amount": 7500,
          "cvv": 123
        }
        """;

    MvcResult createResult = mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(createRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andReturn();

    String responseBody = createResult.getResponse().getContentAsString();
    String paymentId = objectMapper.readTree(responseBody).get("id").asText();

    // Retrieve the payment
    mockMvc.perform(get("/payments/" + paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(4443))
        .andExpect(jsonPath("$.expiryMonth").value(3))
        .andExpect(jsonPath("$.expiryYear").value(2028))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(7500));
  }

  @Test
  void shouldTestBankSimulatorBehaviorBasedOnLastDigit() throws Exception {
    // Test cards ending in different digits to verify bank simulator behavior

    // Odd digits (1, 3, 5, 7, 9) -> Authorized
    for (int lastDigit : new int[]{1, 3, 5, 7, 9}) {
      String cardNumber = String.format("411111111111111%d", lastDigit);
      String request = String.format("""
          {
            "card_number": "%s",
            "expiry_month": 12,
            "expiry_year": 2027,
            "currency": "GBP",
            "amount": 100,
            "cvv": 123
          }
          """, cardNumber);

      mockMvc.perform(post("/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status").value("Authorized"))
          .andExpect(jsonPath("$.cardNumberLastFour").value(Integer.parseInt(String.format("111%d", lastDigit))));
    }

    // Even digits (2, 4, 6, 8) -> Declined
    for (int lastDigit : new int[]{2, 4, 6, 8}) {
      String cardNumber = String.format("411111111111111%d", lastDigit);
      String request = String.format("""
          {
            "card_number": "%s",
            "expiry_month": 12,
            "expiry_year": 2027,
            "currency": "GBP",
            "amount": 100,
            "cvv": 123
          }
          """, cardNumber);

      mockMvc.perform(post("/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.status").value("Declined"))
          .andExpect(jsonPath("$.cardNumberLastFour").value(Integer.parseInt(String.format("111%d", lastDigit))));
    }
  }

  @Test
  void shouldStillRejectInvalidRequestsEvenWithRealBank() throws Exception {
    // Validation happens before calling the bank
    String invalidRequest = """
        {
          "card_number": "123",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }
}
