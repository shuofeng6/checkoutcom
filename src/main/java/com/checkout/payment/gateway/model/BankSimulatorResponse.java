package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BankSimulatorResponse {
  private boolean authorized;

  @JsonProperty("authorization_code")
  private String authorizationCode;

  public BankSimulatorResponse() {
  }

  public BankSimulatorResponse(boolean authorized, String authorizationCode) {
    this.authorized = authorized;
    this.authorizationCode = authorizationCode;
  }

  public boolean isAuthorized() {
    return authorized;
  }

  @Override
  public String toString() {
    return "BankSimulatorResponse{" +
        "authorized=" + authorized +
        ", authorizationCode='" + authorizationCode + '\'' +
        '}';
  }
}
