package uk.ac.ed.acp.cw2.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

@Configuration
@EnableScheduling
public class IlpRestServiceConfig {

   private static final String DEFAULT_ILP = "https://ilp-rest-2025-bvh6e9hschfagrgy.ukwest-01.azurewebsites.net/";

    @Bean
    public Supplier<String> ilpEndpointSupplier() {
        return () -> {
            String env = System.getenv("ILP_ENDPOINT");
            if (env == null || env.isBlank()) return DEFAULT_ILP;
            return env;
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
