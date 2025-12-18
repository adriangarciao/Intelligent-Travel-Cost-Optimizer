package com.adriangarciao.traveloptimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class TraveloptimizerApplication {

	public static void main(String[] args) {
		SpringApplication.run(TraveloptimizerApplication.class, args);
	}

}
