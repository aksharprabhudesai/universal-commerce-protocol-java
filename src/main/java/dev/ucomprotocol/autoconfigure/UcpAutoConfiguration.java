package dev.ucomprotocol.autoconfigure;

import dev.ucomprotocol.ap2.Ap2CommerceAdapter;
import dev.ucomprotocol.ap2.Ap2MandateService;
import dev.ucomprotocol.ap2.JwkKey;
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
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;

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
 * <li>Configures {@link Ap2MandateService} and decorates {@link CommerceAdapter}
 * with {@link Ap2CommerceAdapter} when {@code ucp.ap2.enabled=true}.</li>
 * <li>Registers {@link UcpDiscoveryController} in Servlet web applications when
 * a {@link CommerceAdapter} bean is available.</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(UcpProperties.class)
@Import({
        UcpAdapterConfiguration.class,
        UcpResilienceConfiguration.class,
        UcpAp2Configuration.class,
        UcpWebMvcConfiguration.class
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
@ConditionalOnClass({ CircuitBreakerRegistry.class, RetryRegistry.class })
@ConditionalOnProperty(prefix = "ucp.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
class UcpResilienceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(UcpAutoConfiguration.class);

    @Bean
    static BeanPostProcessor ucpCommerceAdapterResiliencePostProcessor(
            ObjectProvider<UcpProperties> propertiesProvider,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider,
            ObjectProvider<RetryRegistry> retryRegistryProvider) {
        return new OrderedBeanPostProcessor(Ordered.LOWEST_PRECEDENCE - 10) {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof CommerceAdapter adapter)
                        || bean instanceof ResilientCommerceAdapter
                        || bean instanceof Ap2CommerceAdapter) {
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

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ucp.ap2", name = "enabled", havingValue = "true")
class UcpAp2Configuration {

    private static final Logger logger = LoggerFactory.getLogger(UcpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public Ap2MandateService ap2MandateService(UcpProperties properties) {
        UcpProperties.Ap2 ap2 = properties.getAp2();
        String algorithm = ap2.getAlgorithm() != null && !ap2.getAlgorithm().isBlank()
                ? ap2.getAlgorithm().trim()
                : "ES256";
        String keyId = ap2.getKeyId() != null && !ap2.getKeyId().isBlank()
                ? ap2.getKeyId().trim()
                : "merchant-ap2-key-1";

        ECPrivateKey privateKey;
        ECPublicKey publicKey;
        boolean hasPrivate = ap2.getSigningPrivateKey() != null && !ap2.getSigningPrivateKey().isBlank();
        boolean hasPublic = ap2.getSigningPublicKey() != null && !ap2.getSigningPublicKey().isBlank();

        if (hasPrivate && hasPublic) {
            privateKey = Ap2MandateService.parsePrivateKey(ap2.getSigningPrivateKey());
            publicKey = Ap2MandateService.parsePublicKey(ap2.getSigningPublicKey());
            logger.info("Configured AP2MandateService with explicit EC key pair (kid={}, alg={})", keyId, algorithm);
        } else if (!hasPrivate && !hasPublic && ap2.isAutoGenerateEphemeralKey()) {
            KeyPair ephemeral = Ap2MandateService.generateEcKeyPair(algorithm);
            privateKey = (ECPrivateKey) ephemeral.getPrivate();
            publicKey = (ECPublicKey) ephemeral.getPublic();
            logger.info("Configured AP2MandateService with ephemeral EC key pair (kid={}, alg={})", keyId, algorithm);
        } else {
            throw new IllegalStateException(
                    "AP2 is enabled (ucp.ap2.enabled=true) without both ucp.ap2.signing-private-key and "
                            + "ucp.ap2.signing-public-key, and ucp.ap2.auto-generate-ephemeral-key is false");
        }

        List<JwkKey> trustedKeys = ap2.getTrustedPlatformKeys() != null
                ? ap2.getTrustedPlatformKeys().stream().map(UcpProperties.TrustedKey::toJwkKey).toList()
                : List.of();

        return new Ap2MandateService(
                keyId,
                algorithm,
                privateKey,
                publicKey,
                ap2.isRequireCheckoutMandate(),
                ap2.isVerifyMandateSignature(),
                ap2.isAllowEmbeddedAgentJwk(),
                trustedKeys);
    }

    @Bean
    static BeanPostProcessor ucpCommerceAdapterAp2PostProcessor(
            ObjectProvider<Ap2MandateService> ap2MandateServiceProvider) {
        return new OrderedBeanPostProcessor(Ordered.LOWEST_PRECEDENCE) {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (!(bean instanceof CommerceAdapter adapter) || bean instanceof Ap2CommerceAdapter) {
                    return bean;
                }
                Ap2MandateService ap2Service = ap2MandateServiceProvider.getIfAvailable();
                if (ap2Service == null) {
                    return bean;
                }
                logger.debug("Wrapping CommerceAdapter '{}' with Ap2CommerceAdapter", beanName);
                return new Ap2CommerceAdapter(adapter, ap2Service);
            }
        };
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestController.class)
class UcpWebMvcConfiguration {

    @Bean
    @ConditionalOnBean(CommerceAdapter.class)
    @ConditionalOnMissingBean
    public UcpDiscoveryController ucpDiscoveryController(
            UcpProperties properties,
            CommerceAdapter adapter,
            ObjectProvider<Ap2MandateService> ap2MandateServiceProvider) {
        return new UcpDiscoveryController(properties, adapter, ap2MandateServiceProvider.getIfAvailable());
    }
}

abstract class OrderedBeanPostProcessor implements BeanPostProcessor, Ordered {
    private final int order;

    OrderedBeanPostProcessor(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
