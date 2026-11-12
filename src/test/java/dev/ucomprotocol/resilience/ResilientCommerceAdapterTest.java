package dev.ucomprotocol.resilience;

import dev.ucomprotocol.autoconfigure.UcpAutoConfiguration;
import dev.ucomprotocol.model.Product;
import dev.ucomprotocol.spi.CartAdapter;
import dev.ucomprotocol.spi.CatalogAdapter;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.CustomerAdapter;
import dev.ucomprotocol.spi.OrderAdapter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientCommerceAdapterTest {

    @Test
    void shouldRetryTransientFailureAndPreserveNullCapabilities() {
        AtomicInteger attempts = new AtomicInteger();
        CommerceAdapter raw = new CommerceAdapter() {
            @Override
            public CatalogAdapter getCatalogAdapter() {
                return new CatalogAdapter() {
                    @Override
                    public Optional<Product> getProductById(String id) {
                        if (attempts.incrementAndGet() < 3) {
                            throw new RuntimeException("transient failure");
                        }
                        return Optional.of(Product.builder().id(id).name("Recovered").build());
                    }

                    @Override
                    public List<Product> searchProducts(String query) {
                        return List.of();
                    }

                    @Override
                    public List<Product> getProductsByCategory(String categoryId) {
                        return List.of();
                    }
                };
            }

            @Override
            public CartAdapter getCartAdapter() {
                return null;
            }

            @Override
            public OrderAdapter getOrderAdapter() {
                return null;
            }

            @Override
            public CustomerAdapter getCustomerAdapter() {
                return null;
            }
        };

        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .build());
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");

        ResilientCommerceAdapter resilient = new ResilientCommerceAdapter(raw, cb, retry);

        assertThat(resilient.getCartAdapter()).isNull();
        assertThat(resilient.getCheckoutAdapter()).isNull();
        assertThat(resilient.getCatalogAdapter().getProductById("p-1"))
                .isPresent()
                .get()
                .extracting(Product::getName)
                .isEqualTo("Recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldOpenCircuitBreakerAfterThresholdFailures() {
        CommerceAdapter failing = new CommerceAdapter() {
            @Override
            public CatalogAdapter getCatalogAdapter() {
                return new CatalogAdapter() {
                    @Override
                    public Optional<Product> getProductById(String id) {
                        throw new IllegalStateException("downstream down");
                    }

                    @Override
                    public List<Product> searchProducts(String query) {
                        return List.of();
                    }

                    @Override
                    public List<Product> getProductsByCategory(String categoryId) {
                        return List.of();
                    }
                };
            }

            @Override
            public CartAdapter getCartAdapter() {
                return null;
            }

            @Override
            public OrderAdapter getOrderAdapter() {
                return null;
            }

            @Override
            public CustomerAdapter getCustomerAdapter() {
                return null;
            }
        };

        CircuitBreaker cb = CircuitBreaker.of("cb-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .build());

        ResilientCommerceAdapter resilient = new ResilientCommerceAdapter(failing, cb, null);

        assertThatThrownBy(() -> resilient.getCatalogAdapter().getProductById("1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resilient.getCatalogAdapter().getProductById("1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> resilient.getCatalogAdapter().getProductById("1"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void autoConfigurationShouldWrapAdapterWhenRegistriesPresentAndRespectEnabledFlag() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(UcpAutoConfiguration.class))
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withBean(RetryRegistry.class, RetryRegistry::ofDefaults);

        runner.run(ctx -> {
            CommerceAdapter adapter = ctx.getBean(CommerceAdapter.class);
            assertThat(adapter).isInstanceOf(ResilientCommerceAdapter.class);
        });

        runner.withPropertyValues("ucp.resilience.enabled=false").run(ctx -> {
            CommerceAdapter adapter = ctx.getBean(CommerceAdapter.class);
            assertThat(adapter).isNotInstanceOf(ResilientCommerceAdapter.class);
        });
    }
}
