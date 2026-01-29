package com.microsoft.cloudoptimizer.config;

import com.microsoft.cloudoptimizer.adapters.CloudCostAdapter;
import com.microsoft.cloudoptimizer.domain.model.CloudProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration for cloud provider adapters.
 */
@Configuration
public class AdapterConfig {

    @Bean
    public Map<CloudProvider, CloudCostAdapter> costAdapters(List<CloudCostAdapter> adapters) {
        return adapters.stream()
                .collect(Collectors.toMap(
                        CloudCostAdapter::getProvider,
                        Function.identity()
                ));
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
