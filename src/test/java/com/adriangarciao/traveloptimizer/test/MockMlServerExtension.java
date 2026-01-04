package com.adriangarciao.traveloptimizer.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * No-op compatibility shim for legacy tests during migration to WireMock. Remaining references to
 * `MockMlServerExtension` will be harmless no-ops.
 */
public class MockMlServerExtension implements BeforeAllCallback, AfterAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // no-op
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        // no-op
    }
}
