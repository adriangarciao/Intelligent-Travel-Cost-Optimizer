package com.adriangarciao.traveloptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;

@SpringBootApplication
@EnableCaching
public class TraveloptimizerApplication {

	private static final Logger log = LoggerFactory.getLogger(TraveloptimizerApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(TraveloptimizerApplication.class, args);
	}

	@Bean
	public ApplicationListener<ApplicationReadyEvent> readyListener() {
		return event -> {
			var env = event.getApplicationContext().getEnvironment();
			String profiles = String.join(",", env.getActiveProfiles());
			String configuredPort = env.getProperty("server.port", "8080");
			String commit = getGitCommitShort();
			if (commit == null) commit = "build@" + Instant.now().toString();
			log.info("Startup: activeProfile={} configuredPort={} build={}", profiles, configuredPort, commit);
		};
	}

	private static String getGitCommitShort() {
		try {
			Process p = new ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(new java.io.File(".")).start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String s = r.readLine();
				p.waitFor();
				return s;
			}
		} catch (Exception e) {
			return null;
		}
	}

}
