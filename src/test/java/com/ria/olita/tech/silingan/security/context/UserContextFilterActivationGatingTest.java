//package com.ria.olita.tech.silingan.security.context;
//
//import java.time.Duration;
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mockito;
//import org.springframework.mock.web.MockFilterChain;
//import org.springframework.mock.web.MockHttpServletRequest;
//import org.springframework.mock.web.MockHttpServletResponse;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
//
//import com.ria.olita.tech.silingan.dto.res.StaffInvitationAcceptResponse;
//import com.ria.olita.tech.silingan.entity.User;
//import com.ria.olita.tech.silingan.repository.UserRepository;
//import com.ria.olita.tech.silingan.service.CommunityAdminInvitationActivationService;
//import com.ria.olita.tech.silingan.service.CommunityRbacService;
//import com.ria.olita.tech.silingan.service.StaffInvitationActivationService;
//
//import jakarta.persistence.EntityManager;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * Invitation activation used to run on every authenticated request: a database probe for every
// * user forever, plus a Keycloak round trip for anyone with an outstanding invitation. These tests
// * pin the gating that removed it from the hot path, and - just as importantly - the cases where
// * the checks must still run.
// */
//class UserContextFilterActivationGatingTest {
//
//	private static final String SUBJECT = "kc-user-1";
//	private static final String EMAIL = "jane@example.com";
//
//	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
//	private final CommunityRbacService communityRbacService = Mockito.mock(CommunityRbacService.class);
//	private final CommunityAdminInvitationActivationService adminActivationService =
//		Mockito.mock(CommunityAdminInvitationActivationService.class);
//	private final StaffInvitationActivationService staffActivationService =
//		Mockito.mock(StaffInvitationActivationService.class);
//	private final EntityManager entityManager = Mockito.mock(EntityManager.class);
//
//	private UserContextFilter filterWith(InvitationActivationGate gate) {
//		return new UserContextFilter(
//			userRepository,
//			communityRbacService,
//			adminActivationService,
//			staffActivationService,
//			gate,
//			entityManager
//		);
//	}
//
//	private InvitationActivationGate gate(Duration settledTtl, Duration unsettledTtl) {
//		return new InvitationActivationGate(settledTtl, unsettledTtl, 100);
//	}
//
//	private InvitationActivationGate gate() {
//		return gate(Duration.ofMinutes(30), Duration.ofSeconds(30));
//	}
//
//	@AfterEach
//	void clearSecurityContext() {
//		SecurityContextHolder.clearContext();
//		UserContextHolder.clear();
//	}
//
//	/**
//	 * Drives one request through the filter. The user has no selected community, which keeps the
//	 * Hibernate community filter out of the picture - this test is about activation gating.
//	 */
//	private void request(UserContextFilter filter) throws Exception {
//		Jwt jwt = Jwt.withTokenValue("token")
//			.header("alg", "none")
//			.claim("sub", SUBJECT)
//			.claim("email", EMAIL)
//			.build();
//		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
//
//		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
//	}
//
//	private void givenLocalUserExists() {
//		User user = User.builder()
//			.id(UUID.randomUUID())
//			.keycloakUserId(SUBJECT)
//			.email(EMAIL)
//			.build();
//		Mockito.when(userRepository.findByKeycloakUserId(SUBJECT)).thenReturn(Optional.of(user));
//	}
//
//	private void givenNothingPending() {
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL)).thenReturn(false);
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(false);
//	}
//
//	@Test
//	void firstRequestRunsBothActivationProbes() throws Exception {
//		givenLocalUserExists();
//		givenNothingPending();
//
//		request(filterWith(gate()));
//
//		Mockito.verify(staffActivationService).hasActivePendingInvitation(SUBJECT, EMAIL);
//		Mockito.verify(adminActivationService).hasActivePendingInvitation(SUBJECT);
//	}
//
//	@Test
//	void subsequentRequestsSkipActivationEntirely() throws Exception {
//		givenLocalUserExists();
//		givenNothingPending();
//		UserContextFilter filter = filterWith(gate());
//
//		request(filter);
//		Mockito.clearInvocations(staffActivationService, adminActivationService);
//
//		request(filter);
//		request(filter);
//		request(filter);
//
//		// This is the whole point of the change: no invitation work on the hot path.
//		Mockito.verifyNoInteractions(staffActivationService);
//		Mockito.verifyNoInteractions(adminActivationService);
//	}
//
//	@Test
//	void userContextIsStillPopulatedWhenActivationIsSkipped() throws Exception {
//		givenLocalUserExists();
//		givenNothingPending();
//		UserContextFilter filter = filterWith(gate());
//
//		request(filter);
//
//		// Second request: capture the context from inside the chain, before the filter clears it.
//		Jwt jwt = Jwt.withTokenValue("token")
//			.header("alg", "none")
//			.claim("sub", SUBJECT)
//			.claim("email", EMAIL)
//			.build();
//		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
//
//		UserContext[] seen = new UserContext[1];
//		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
//			(req, res) -> seen[0] = UserContextHolder.get());
//
//		assertThat(seen[0]).isNotNull();
//		assertThat(seen[0].keycloakUserId()).isEqualTo(SUBJECT);
//	}
//
//	@Test
//	void anInviteeMidOnboardingIsRecheckedPromptly() throws Exception {
//		givenLocalUserExists();
//		// Invitation exists but Keycloak required actions are outstanding, so nothing activates.
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL)).thenReturn(true);
//		Mockito.when(staffActivationService.activateIfCompleted(SUBJECT, EMAIL))
//			.thenReturn(StaffInvitationAcceptResponse.pendingRequiredActions());
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(false);
//
//		UserContextFilter filter = filterWith(gate(Duration.ofMinutes(30), Duration.ofMillis(50)));
//
//		request(filter);
//		Mockito.clearInvocations(staffActivationService);
//		Thread.sleep(150);
//		request(filter);
//
//		// Must not be cached as settled - the invitee would otherwise be locked out until the
//		// long TTL expired, even after completing their required actions.
//		Mockito.verify(staffActivationService).hasActivePendingInvitation(SUBJECT, EMAIL);
//	}
//
//	@Test
//	void successfulActivationSettlesTheIdentity() throws Exception {
//		givenLocalUserExists();
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL)).thenReturn(true);
//		Mockito.when(staffActivationService.activateIfCompleted(SUBJECT, EMAIL))
//			.thenReturn(StaffInvitationAcceptResponse.accepted(List.of(UUID.randomUUID())));
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(false);
//
//		UserContextFilter filter = filterWith(gate());
//
//		request(filter);
//		Mockito.clearInvocations(staffActivationService, adminActivationService);
//		request(filter);
//
//		Mockito.verifyNoInteractions(staffActivationService);
//	}
//
//	@Test
//	void aFailedActivationIsNotCachedAsSettled() throws Exception {
//		givenLocalUserExists();
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL))
//			.thenThrow(new IllegalStateException("keycloak unavailable"));
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(false);
//
//		UserContextFilter filter = filterWith(gate(Duration.ofMinutes(30), Duration.ofMillis(50)));
//
//		request(filter);
//		Mockito.clearInvocations(staffActivationService);
//		Thread.sleep(150);
//		request(filter);
//
//		// A transient Keycloak outage must not permanently mark the user as having nothing to do.
//		Mockito.verify(staffActivationService).hasActivePendingInvitation(SUBJECT, EMAIL);
//	}
//
//	@Test
//	void activationFailureDoesNotBreakTheRequest() throws Exception {
//		givenLocalUserExists();
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL))
//			.thenThrow(new IllegalStateException("keycloak unavailable"));
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(false);
//
//		MockFilterChain chain = new MockFilterChain();
//		Jwt jwt = Jwt.withTokenValue("token")
//			.header("alg", "none")
//			.claim("sub", SUBJECT)
//			.claim("email", EMAIL)
//			.build();
//		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
//
//		filterWith(gate()).doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);
//
//		assertThat(chain.getRequest()).isNotNull();
//	}
//
//	@Test
//	void adminInvitationStillPendingKeepsTheIdentityUnsettled() throws Exception {
//		givenLocalUserExists();
//		Mockito.when(staffActivationService.hasActivePendingInvitation(SUBJECT, EMAIL)).thenReturn(false);
//		// Pending before and after: the user has no selected community, so there is nothing the
//		// filter can activate yet.
//		Mockito.when(adminActivationService.hasActivePendingInvitation(SUBJECT)).thenReturn(true);
//
//		UserContextFilter filter = filterWith(gate(Duration.ofMinutes(30), Duration.ofMillis(50)));
//
//		request(filter);
//		Mockito.clearInvocations(adminActivationService);
//		Thread.sleep(150);
//		request(filter);
//
//		Mockito.verify(adminActivationService, Mockito.atLeastOnce()).hasActivePendingInvitation(SUBJECT);
//	}
//
//	@Test
//	void requestsWithoutAJwtNeverTouchActivation() throws Exception {
//		filterWith(gate()).doFilter(
//			new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());
//
//		Mockito.verifyNoInteractions(staffActivationService);
//		Mockito.verifyNoInteractions(adminActivationService);
//		Mockito.verifyNoInteractions(userRepository);
//	}
//
//	@Test
//	void userContextIsClearedAfterTheRequest() throws Exception {
//		givenLocalUserExists();
//		givenNothingPending();
//
//		request(filterWith(gate()));
//
//		// Thread-local leakage across pooled request threads would hand one user another user's
//		// identity.
//		assertThat(UserContextHolder.get()).isNull();
//	}
//}
