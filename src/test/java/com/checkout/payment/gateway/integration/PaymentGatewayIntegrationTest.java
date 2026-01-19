package com.checkout.payment.gateway.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.model.BankSimulatorResponse;
import com.checkout.payment.gateway.service.BankSimulatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests with mocked external dependencies.
 * These tests verify the application logic without requiring external services.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private BankSimulatorService bankSimulatorService;

  @Test
  void shouldProcessAuthorizedPayment() throws Exception {
    // Mock bank response for authorized payment
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-code-123");
    when(bankSimulatorService.authorize(any())).thenReturn(bankResponse);

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
  void shouldProcessDeclinedPayment() throws Exception {
    // Mock bank response for declined payment
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(false, null);
    when(bankSimulatorService.authorize(any())).thenReturn(bankResponse);

    String requestJson = """
        {
          "card_number": "2222405343248888",
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
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8888));
  }

  @Test
  void shouldRejectPaymentWithInvalidCardNumber() throws Exception {
    String requestJson = """
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
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Card number must be between 14-19 characters")));
  }

  @Test
  void shouldRejectPaymentWithInvalidExpiryMonth() throws Exception {
    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 13,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Expiry month must be between 1-12")));
  }

  @Test
  void shouldRejectPaymentWithExpiredCard() throws Exception {
    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 1,
          "expiry_year": 2020,
          "currency": "GBP",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void shouldRejectPaymentWithUnsupportedCurrency() throws Exception {
    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "JPY",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Currency must be one of: USD, GBP, EUR")));
  }

  @Test
  void shouldRejectPaymentWithInvalidAmount() throws Exception {
    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": -100,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Amount must be a positive integer")));
  }

  @Test
  void shouldRetrievePaymentById() throws Exception {
    // First, create a payment
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-code-456");
    when(bankSimulatorService.authorize(any())).thenReturn(bankResponse);

    String requestJson = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "USD",
          "amount": 500,
          "cvv": 456
        }
        """;

    MvcResult createResult = mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isCreated())
        .andReturn();

    String responseBody = createResult.getResponse().getContentAsString();
    String paymentId = objectMapper.readTree(responseBody).get("id").asText();

    // Then, retrieve the payment
    mockMvc.perform(get("/payments/" + paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(paymentId))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(500));
  }

  @Test
  void shouldReturn404ForNonExistentPayment() throws Exception {
    UUID randomId = UUID.randomUUID();

    mockMvc.perform(get("/payments/" + randomId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").exists());
  }

  @Test
  void shouldAcceptAllSupportedCurrencies() throws Exception {
    BankSimulatorResponse bankResponse = new BankSimulatorResponse(true, "auth-code-789");
    when(bankSimulatorService.authorize(any())).thenReturn(bankResponse);

    // Test USD
    String usdRequest = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "USD",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(usdRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("USD"));

    // Test EUR
    String eurRequest = """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "EUR",
          "amount": 1000,
          "cvv": 123
        }
        """;

    mockMvc.perform(post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(eurRequest))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("EUR"));
  }
}
