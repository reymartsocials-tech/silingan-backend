package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ria.olita.tech.silingan.dto.req.PlatformGrowthPeriod;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.service.PlatformDashboardService;

class PlatformDashboardControllerTest {

	@Test
	void onlyPlatformAdminRoleMayCallTheEndpoint() {
		PreAuthorize pre = PlatformDashboardController.class.getAnnotation(PreAuthorize.class);
		assertThat(pre).isNotNull();
		assertThat(pre.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
		assertThat(pre.value()).doesNotContain("COMMUNITY_ADMIN");
	}

	@Test
	void growthPeriodIsParsedStrictly() {
		PlatformDashboardService service = mock(PlatformDashboardService.class);
		PlatformDashboardController controller = new PlatformDashboardController(service);

		controller.getDashboard("12_MONTHS");
		ArgumentCaptor<PlatformGrowthPeriod> captor = ArgumentCaptor.forClass(PlatformGrowthPeriod.class);
		verify(service).getDashboard(captor.capture());
		assertThat(captor.getValue()).isEqualTo(PlatformGrowthPeriod.TWELVE_MONTHS);

		assertThatThrownBy(() -> controller.getDashboard("3_MONTHS")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> controller.getDashboard("6_months")).isInstanceOf(ValidationException.class);
	}

	@Test
	void defaultIsSixMonths() {
		assertThat(PlatformGrowthPeriod.fromValue(PlatformGrowthPeriod.DEFAULT_VALUE))
			.isEqualTo(PlatformGrowthPeriod.SIX_MONTHS);
	}
}
