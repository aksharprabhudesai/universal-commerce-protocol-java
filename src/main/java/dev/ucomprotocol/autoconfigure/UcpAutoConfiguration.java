package dev.ucomprotocol.autoconfigure;

import dev.ucomprotocol.discovery.UcpDiscoveryController;
import dev.ucomprotocol.resilience.ResilientCommerceAdapter;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.mock.MockCommerceAdapter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-configuration for the Universal Commerce Protocol (UCP) library.
 * <p>
 * This configuration:
 * <ul>
 * <li>Enables {@link UcpProperties} binding.</li>
 * <li>Configures a default {@link MockCommerceAdapter} if none is provided and
 * {@code ucp.provider=mock}.</li>
 * <li>Decorates {@link CommerceAdapter} beans with Resilience4j circuit breaker
 * and retry protection when Resilience4j registries are active.</li>
 * <li>Registers {@link UcpDiscoveryController} in Servlet web applications when
 * a {@link CommerceAdapter} bean is available.</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(UcpProperties.class)
@Import({
        UcpAdapterConfiguration.class,
        UcpWebMvcConfiguration.class,
        UcpResilienceConfiguration.class
})
public class UcpAutoConfiguration {
}

@Configuration(proxyBeanMethods = false)
class UcpAdapterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(UcpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommerceAdapter.class)
    @ConditionalOnProperty(name = "ucp.provider", havingValue = "mock", matchIfMissing = true)
    public CommerceAdapter mockCommerceAdapter() {
        logger.info("Configuring Mock Commerce Adapter");
        return new MockCommerceAdapter();
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestController.class)
class UcpWebMvcConfiguration {

    @Bean
    @ConditionalOnBean(CommerceAdapter.class)
    @ConditionalOnMissingBean
    public UcpDiscoveryController ucpDiscoveryController(UcpProperties properties,
            CommerceAdapter adapter) {
        return new UcpDiscoveryController(properties, adapter);
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ CircuitBreakerRegistry.class, RetryRegistry.class })
@ConditionalOnProperty(prefix = "ucp.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
class UcpResilienceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(UcpAutoConfiguration.class);

    @Bean
    static BeanPostProcessor ucpCommerceAdapterResiliencePostProcessor(
            ObjectProvider<UcpProperties> propertiesProvider,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
            ObjectProvider<RetryRegistry> retryRegistryProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof CommerceAdapter adapter) || bean instanceof ResilientCommerceAdapter) {
                    return bean;
                }
                CircuitBreakerRegistry cbRegistry = circuitBreakerRegistryProvider.getIfAvailable();
                RetryRegistry retryRegistry = retryRegistryProvider.getIfAvailable();
                if (cbRegistry == null && retryRegistry == null) {
                    return bean;
                }
                UcpProperties props = propertiesProvider.getIfAvailable(UcpProperties::new);
                if (!props.getResilience().isEnabled()) {
                    return bean;
                }
                String instanceName = props.getResilience().getInstanceName();
                if (instanceName == null || instanceName.isBlank()) {
                    instanceName = "ucp";
                }
                CircuitBreaker cb = cbRegistry != null ? cbRegistry.circuitBreaker(instanceName) : null;
                Retry retry = retryRegistry != null ? retryRegistry.retry(instanceName) : null;
                logger.debug("Wrapping CommerceAdapter '{}' with Resilience4j instance '{}'", beanName,
                        instanceName);
                return new ResilientCommerceAdapter(adapter, cb, retry);
            }
        };
    }
}
