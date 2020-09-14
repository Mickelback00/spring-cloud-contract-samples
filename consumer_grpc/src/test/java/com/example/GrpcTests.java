package com.example;

import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.cloud.contract.stubrunner.HttpServerStubConfiguration;
import org.springframework.cloud.contract.stubrunner.junit.StubRunnerExtension;
import org.springframework.cloud.contract.stubrunner.provider.wiremock.WireMockHttpServerStubConfigurer;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @author Marcin Grzejszczak
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE, classes = GrpcTests.TestConfiguration.class, properties = {
		"grpc.client.beerService.address=static://localhost:5432", "grpc.client.beerService.negotiationType=TLS"
})
// @org.junit.Ignore
public class GrpcTests {

	@GrpcClient("beerService")
	BeerServiceGrpc.BeerServiceBlockingStub beerServiceBlockingStub;

	int port;

	//remove::start[]
	@RegisterExtension
	static StubRunnerExtension rule = new StubRunnerExtension()
			.downloadStub("com.example", "beer-api-producer-grpc")
			.stubsMode(StubRunnerProperties.StubsMode.LOCAL)
			.withHttpServerStubConfigurer(MyWireMockConfigurer.class);

	// remove::end[]

	@BeforeAll
	public static void beforeClass() {
		Assumptions.assumeTrue(atLeast300(), "Spring Cloud Contract must be in version at least 3.0.0");
		Assumptions.assumeTrue(StringUtils.isEmpty(System.getenv("OLD_PRODUCER_TRAIN")),
				"Env var OLD_PRODUCER_TRAIN must not be set");
	}

	@BeforeEach
	public void setupPort() {
		//remove::start[]
		this.port = rule.findStubUrl("beer-api-producer-grpc").getPort();
		// remove::end[]
	}

	private static boolean atLeast300() {
		try {
			Class.forName("org.springframework.cloud.contract.verifier.dsl.wiremock.SpringCloudContractRequestMatcher");
		} catch (Exception ex) {
			return false;
		}
		return true;
	}

	// remove::end[]
	// tag::tests[]
	@Test
	public void should_give_me_a_beer_when_im_old_enough() throws Exception {
		//remove::start[]
		Response response = beerServiceBlockingStub.check(PersonToCheck.newBuilder().setAge(23).build());

		BDDAssertions.then(response.getStatus()).isEqualTo(Response.BeerCheckStatus.OK);
		// remove::end[]
	}

	@Test
	public void should_reject_a_beer_when_im_too_young() throws Exception {
		//remove::start[]
		Response response = beerServiceBlockingStub.check(PersonToCheck.newBuilder().setAge(17).build());
		// TODO: If someone knows how to do this properly for default responses that would be helpful
		response = response == null ? Response.newBuilder().build() : response;

		BDDAssertions.then(response.getStatus()).isEqualTo(Response.BeerCheckStatus.NOT_OK);
		// remove::end[]
	}
	// end::tests[]

	static class MyWireMockConfigurer extends WireMockHttpServerStubConfigurer {
		@Override
		public WireMockConfiguration configure(WireMockConfiguration httpStubConfiguration, HttpServerStubConfiguration httpServerStubConfiguration) {
			return httpStubConfiguration
					.httpsPort(5432);
		}
	}

	@Configuration
	@ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
	static class TestConfiguration {

		@Bean
		public GrpcChannelConfigurer keepAliveClientConfigurer() {
			return (channelBuilder, name) -> {
				if (channelBuilder instanceof NettyChannelBuilder) {
					try {
						((NettyChannelBuilder) channelBuilder)
								.sslContext(GrpcSslContexts.forClient()
										.trustManager(InsecureTrustManagerFactory.INSTANCE)
										.build());
					}
					catch (SSLException e) {
						throw new IllegalStateException(e);
					}
				}
			};
		}

	}
}
