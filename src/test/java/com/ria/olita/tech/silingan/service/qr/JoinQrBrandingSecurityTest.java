package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.exception.ValidationException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Branding must stay a server-owned, presentation-only concern: no caller may choose what a join QR
 * encodes or how it is branded, and nothing token-bearing may reach the logs.
 */
class JoinQrBrandingSecurityTest {

	private static final String TOKEN = JoinQrTestFixtures.SAMPLE_TOKEN;
	private static final String JOIN_URL = "https://app.silingan.ph/join?token=" + TOKEN;

	private final Logger qrLogger = (Logger) LoggerFactory.getLogger("com.ria.olita.tech.silingan.service.qr");
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void captureLogs() {
		appender.start();
		qrLogger.addAppender(appender);
		qrLogger.setLevel(Level.TRACE);
	}

	@AfterEach
	void releaseLogs() {
		qrLogger.detachAppender(appender);
		appender.stop();
	}

	@Test
	void theInvitationRequestCannotCarryAnyQrOrBrandingInput() {
		List<String> fields = Arrays.stream(CreateCommunityJoinInvitationRequest.class.getRecordComponents())
			.map(RecordComponent::getName)
			.toList();

		assertThat(fields).containsExactlyInAnyOrder("label", "distributionChannel", "expiresInHours", "maxUses");
		assertThat(fields).noneMatch(field -> {
			String name = field.toLowerCase(Locale.ROOT);
			return name.contains("url") || name.contains("logo") || name.contains("tagline")
				|| name.contains("colour") || name.contains("color") || name.contains("qr")
				|| name.contains("content") || name.contains("token") || name.contains("brand");
		});
	}

	@Test
	void theWordmarkAndTaglineComeFromTheApplicationNotFromCallers() {
		assertThat(BrandedQrRenderer.WORDMARK).isEqualTo("SILINGAN");
		assertThat(JoinQrTestFixtures.properties().getQr().getTagline())
			.isEqualTo("Your community, in one place.");
	}

	@Test
	void theBrandingResourceIsAFixedClasspathAsset() {
		assertThat(JoinQrTestFixtures.properties().getQr().getLogoResource())
			.isEqualTo("classpath:branding/silingan-icon.png")
			.startsWith("classpath:");
	}

	@Test
	void refusesToEncodeEmptyContent() {
		QrCodeGenerator generator = JoinQrTestFixtures.generator();

		assertThatThrownBy(() -> generator.generatePng(" ")).isInstanceOf(ValidationException.class);
	}

	@Test
	void neverLogsTheTokenTheJoinUrlOrTheImageItself() {
		byte[] png = JoinQrTestFixtures.generator().generatePng(JOIN_URL);
		String base64 = java.util.Base64.getEncoder().encodeToString(png);

		assertThat(appender.list)
			.allSatisfy(event -> {
				String rendered = event.getFormattedMessage();
				assertThat(rendered).doesNotContain(TOKEN);
				assertThat(rendered).doesNotContain(JOIN_URL);
				assertThat(rendered).doesNotContain(base64.substring(0, 32));
			});
	}
}
