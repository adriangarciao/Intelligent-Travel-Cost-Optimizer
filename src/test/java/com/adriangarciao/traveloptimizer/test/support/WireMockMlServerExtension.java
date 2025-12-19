package com.adriangarciao.traveloptimizer.test.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * JUnit 5 extension that starts a WireMockServer on a dynamic port and
 * registers basic ML stubs used by integration tests.
 */
public class WireMockMlServerExtension implements BeforeAllCallback, AfterAllCallback {

    private WireMockServer server;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        this.server.start();

        // register minimal ML stubs used by tests
        this.server.stubFor(post(urlEqualTo("/predict/best-date-window"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"recommendedDepartureDate\":\"2025-12-30\",\"recommendedReturnDate\":\"2026-01-03\",\"confidence\":0.42}")
                        .withStatus(200)));

        this.server.stubFor(post(urlEqualTo("/predict/option-recommendation"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"isGoodDeal\":true,\"priceTrend\":\"stable\",\"note\":\"mocked\"}")
                        .withStatus(200)));
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (this.server != null && this.server.isRunning()) {
            this.server.stop();
        }
    }

    public WireMockServer getServer() {
        return this.server;
    }
}
