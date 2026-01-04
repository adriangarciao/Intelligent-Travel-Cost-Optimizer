package com.adriangarciao.traveloptimizer.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {"ml.client=webclient", "ml.enabled=true"})
public class MlClientSelectionWebClientTest {

    @Autowired private ApplicationContext ctx;

    @Test
    void when_webclient_selected_only_webclient_present() {
        String[] names = ctx.getBeanNamesForType(MlClient.class);
        assertThat(names).isNotEmpty();
        boolean anyStub =
                java.util.Arrays.stream(names)
                        .anyMatch(
                                n ->
                                        n.toLowerCase().contains("simplemlclient")
                                                || n.toLowerCase().contains("stub"));
        assertThat(anyStub).isFalse();
    }
}
