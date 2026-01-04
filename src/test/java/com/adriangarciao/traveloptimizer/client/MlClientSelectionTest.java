package com.adriangarciao.traveloptimizer.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {"ml.client=stub", "ml.enabled=true"})
public class MlClientSelectionTest {

    @Autowired private ApplicationContext ctx;

    @Test
    void when_stub_selected_only_stub_present() {
        String[] names = ctx.getBeanNamesForType(MlClient.class);
        assertThat(names).isNotEmpty();
        // only the stub should be present in this configuration
        boolean anyWeb =
                java.util.Arrays.stream(names).anyMatch(n -> n.toLowerCase().contains("webclient"));
        assertThat(anyWeb).isFalse();
    }
}
