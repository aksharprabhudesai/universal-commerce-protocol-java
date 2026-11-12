package dev.ucomprotocol.autoconfigure;

import dev.ucomprotocol.discovery.UcpDiscoveryController;
import dev.ucomprotocol.spi.CommerceAdapter;
import dev.ucomprotocol.spi.mock.MockCommerceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class UcpAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UcpAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UcpAutoConfiguration.class));

    @Test
    void shouldConfigureMockAdapterByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CommerceAdapter.class);
            assertThat(context).hasSingleBean(MockCommerceAdapter.class);
            assertThat(context).doesNotHaveBean(UcpDiscoveryController.class);
        });
    }

    @Test
    void shouldConfigureMockAdapterWhenPropertyIsMock() {
        contextRunner
                .withPropertyValues("ucp.provider=mock")
                .run(context -> {
                    assertThat(context).hasSingleBean(CommerceAdapter.class);
                    assertThat(context).hasSingleBean(MockCommerceAdapter.class);
                });
    }

    @Test
    void shouldNotConfigureMockAdapterWhenPropertyIsNotMock() {
        contextRunner
                .withPropertyValues("ucp.provider=shopify")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CommerceAdapter.class);
                });
    }

    @Test
    void shouldNotFailWebContextWhenProviderHasNoAdapterBean() {
        webContextRunner
                .withPropertyValues("ucp.provider=shopify")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CommerceAdapter.class);
                    assertThat(context).doesNotHaveBean(UcpDiscoveryController.class);
                });
    }

    @Test
    void shouldRegisterDiscoveryControllerInWebContextWhenAdapterExists() {
        webContextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(CommerceAdapter.class);
                    assertThat(context).hasSingleBean(UcpDiscoveryController.class);
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesCommerceAdapter() {
        contextRunner
                .withUserConfiguration(UserConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CommerceAdapter.class);
                    assertThat(context).hasBean("userCommerceAdapter");
                    assertThat(context).doesNotHaveBean(MockCommerceAdapter.class);
                });
    }

    @Configuration
    static class UserConfiguration {
        @Bean
        CommerceAdapter userCommerceAdapter() {
            return new CommerceAdapter() {
                @Override
                public dev.ucomprotocol.spi.CatalogAdapter getCatalogAdapter() {
                    return null;
                }

                @Override
                public dev.ucomprotocol.spi.CartAdapter getCartAdapter() {
                    return null;
                }

                @Override
                public dev.ucomprotocol.spi.OrderAdapter getOrderAdapter() {
                    return null;
                }

                @Override
                public dev.ucomprotocol.spi.CustomerAdapter getCustomerAdapter() {
                    return null;
                }
            };
        }
    }
}
