package com.adriangarciao.traveloptimizer.config;

import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-only component that logs which FlightSearchProvider beans are present and the configured
 * provider selection. Does not log secrets.
 */
@Component
@Profile("dev-no-security")
public class ProviderVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProviderVerifier.class);

    private final ApplicationContext ctx;

    public ProviderVerifier(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String selected = ctx.getEnvironment().getProperty("travel.providers.flights");
            Map<String, FlightSearchProvider> beans =
                    ctx.getBeansOfType(FlightSearchProvider.class);
            if (beans.isEmpty()) {
                log.warn(
                        "[DEV] travel.providers.flights={} but no FlightSearchProvider beans were found",
                        selected);
            } else if (beans.size() == 1) {
                FlightSearchProvider p = beans.values().iterator().next();
                log.info(
                        "[DEV] travel.providers.flights={} -> active provider bean={} ({})",
                        selected,
                        p.getClass().getName(),
                        p.getClass().getSimpleName());
            } else {
                // multiple beans — list them
                StringBuilder sb = new StringBuilder();
                beans.forEach(
                        (name, bean) ->
                                sb.append(name)
                                        .append("= ")
                                        .append(bean.getClass().getName())
                                        .append("; "));
                log.info(
                        "[DEV] travel.providers.flights={} -> multiple FlightSearchProvider beans: {}",
                        selected,
                        sb.toString());
            }
        } catch (Exception e) {
            log.warn(
                    "[DEV] ProviderVerifier failed to determine active provider: {}",
                    e.getMessage());
        }
    }
}
