package com.ria.olita.tech.silingan.service.impl;

import com.ria.olita.tech.silingan.dto.req.AnnouncementFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.res.AnnouncementResponse;
import com.ria.olita.tech.silingan.dto.res.AnnouncementInteractionResponse;
import com.ria.olita.tech.silingan.dto.res.AttachmentResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentAnnouncementResponse;
import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;
import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.AttachmentReference;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationPriority;
import com.ria.olita.tech.silingan.entity.NotificationReferenceType;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.AnnouncementMapper;
import com.ria.olita.tech.silingan.repository.AnnouncementAcknowledgementRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementHelpfulMarkRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementInteractionCount;
import com.ria.olita.tech.silingan.repository.AnnouncementRepository;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.repository.spec.AnnouncementSpecifications;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.AnnouncementService;
import com.ria.olita.tech.silingan.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnnouncementServiceImpl implements AnnouncementService {

	private static final Sort DEFAULT_SORT = Sort.by(
		Sort.Order.desc("pinned"),
		Sort.Order.desc("createdAt")
	);

	private static final int RESIDENT_DEFAULT_PAGE_SIZE = 20;
	private static final int RESIDENT_MAX_PAGE_SIZE = 100;

	private final AnnouncementRepository announcementRepository;
	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final UserRepository userRepository;
	private final AttachmentReferenceRepository attachmentReferenceRepository;
	private final AnnouncementAcknowledgementRepository acknowledgementRepository;
	private final AnnouncementHelpfulMarkRepository helpfulMarkRepository;
	private final AnnouncementInteractionRecorder interactionRecorder;
	private final AnnouncementMapper announcementMapper;
	private final NotificationService notificationService;

	@Override
	@Transactional
	public AnnouncementResponse createAnnouncement(CreateAnnouncementRequest request) {
		Community community = requireActiveCommunity(request.communityId());
		requireManageAccess(community.getId());

		Announcement announcement = announcementMapper.toEntity(request);
		announcement.setTitle(requireText(request.title(), "Title"));
		announcement.setContent(requireText(request.content(), "Description"));
		announcement.setStatus(AnnouncementStatus.DRAFT);
		assignCommunity(announcement, community);

		Announcement saved = announcementRepository.save(announcement);
		return buildAnnouncementResponseWithAttachments(saved);
	}

	@Override
	@Transactional
	public AnnouncementResponse updateAnnouncement(UUID id, UpdateAnnouncementRequest request) {
		Announcement announcement = requireAnnouncement(id);
		requireManageAccess(announcement.getCommunityId());

		if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
			throw new ValidationException("Only draft announcements can be updated");
		}

		if (request.title() != null) {
			announcement.setTitle(requireText(request.title(), "Title"));
		}
		if (request.content() != null) {
			announcement.setContent(requireText(request.content(), "Description"));
		}
		if (request.category() != null) {
			announcement.setCategory(request.category());
		}
		announcement.setPinned(request.pinned());

		Announcement saved = announcementRepository.save(announcement);
		return buildAnnouncementResponseWithAttachments(saved);
	}

	@Override
	public Page<AnnouncementResponse> getCommunityAnnouncements(UUID communityId, Pageable pageable) {
		requireViewAccess(requireActiveCommunity(communityId).getId());

		return announcementRepository.findByCommunityId(communityId, pageable)
			.map(this::buildAnnouncementResponseWithAttachments);
	}

	@Override
	public Page<AnnouncementResponse> getCommunityAnnouncementsWithFilters(UUID communityId, AnnouncementFilterCriteria criteria, Pageable pageable) {
		if (communityId == null && !UserContextHolder.isPlatformAdmin()) {
			throw new ValidationException("Community ID is required for non-admin users");
		}

		if (communityId != null) {
			requireViewAccess(requireActiveCommunity(communityId).getId());
		}

		return announcementRepository.findAll(
			AnnouncementSpecifications.filterByCommunityAndCriteria(communityId, criteria),
			applyDefaultSort(pageable)
		).map(this::buildAnnouncementResponseWithAttachments);
	}

	@Override
	public Page<AnnouncementResponse> getActiveCommunityAnnouncements(UUID communityId, Pageable pageable) {
		requireViewAccess(requireActiveCommunity(communityId).getId());

		return announcementRepository.findActiveByCommunityId(communityId, AnnouncementStatus.PUBLISHED, pageable)
			.map(this::buildAnnouncementResponseWithAttachments);
	}

	@Override
	public AnnouncementResponse getAnnouncementById(UUID id) {
		Announcement announcement = announcementRepository.findByIdWithDetails(id)
			.orElseThrow(() -> new NotFoundException("Announcement not found with id: " + id));
		requireViewAccess(announcement.getCommunityId());
		return buildAnnouncementResponseWithAttachments(announcement);
	}

	@Override
	@Transactional
	public void deleteAnnouncement(UUID id) {
		Announcement announcement = requireAnnouncement(id);
		requireManageAccess(announcement.getCommunityId());

		if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
			throw new ValidationException("Only draft announcements can be deleted");
		}

		announcement.setDeleted(true);
		announcementRepository.save(announcement);
	}

	@Override
	@Transactional
	public AnnouncementResponse publishAnnouncement(UUID id) {
		Announcement announcement = requireAnnouncement(id);
		requireManageAccess(announcement.getCommunityId());

		if (announcement.getStatus() == AnnouncementStatus.PUBLISHED) {
			// Idempotent: the original publication timestamp and publisher are preserved.
			return buildAnnouncementResponseWithAttachments(announcement);
		}

		if (announcement.getStatus() != AnnouncementStatus.DRAFT) {
			throw new ValidationException("Only draft announcements can be published");
		}

		announcement.setStatus(AnnouncementStatus.PUBLISHED);
		announcement.setPublishedAt(LocalDateTime.now());
		announcement.setPublishedBy(resolveCurrentUser());

		Announcement saved = announcementRepository.save(announcement);
		notificationService.createNotificationsForCommunityResidents(CreateCommunityNotificationCommand.builder()
			.communityId(saved.getCommunityId())
			.type(NotificationType.ANNOUNCEMENT_PUBLISHED)
			.category(NotificationCategory.ANNOUNCEMENT)
			.priority(saved.isPinned() ? NotificationPriority.IMPORTANT : NotificationPriority.NORMAL)
			.title(saved.getTitle())
			.body(toNotificationBody(saved.getContent()))
			.referenceType(NotificationReferenceType.ANNOUNCEMENT)
			.referenceId(saved.getId().toString())
			.actionPath("/announcements/" + saved.getId())
			.sourceEventId("announcement-published:" + saved.getId())
			.build());
		return buildAnnouncementResponseWithAttachments(saved);
	}

	@Override
	@Transactional
	public AnnouncementResponse unpublishAnnouncement(UUID id) {
		Announcement announcement = requireAnnouncement(id);
		requireManageAccess(announcement.getCommunityId());

		if (announcement.getStatus() != AnnouncementStatus.PUBLISHED) {
			throw new ValidationException("Only published announcements can be unpublished");
		}

		announcement.setStatus(AnnouncementStatus.DRAFT);
		announcement.setPublishedAt(null);
		announcement.setPublishedBy(null);

		Announcement saved = announcementRepository.save(announcement);
		return buildAnnouncementResponseWithAttachments(saved);
	}

	@Override
	public Page<ResidentAnnouncementResponse> getPublishedAnnouncementsForResident(Pageable pageable) {
		UserCommunity membership = resolveResidentActiveMembership();

		Page<Announcement> announcements = announcementRepository.findPublishedByCommunityId(
			membership.getCommunityId(), toResidentPageable(pageable));

		List<UUID> announcementIds = announcements.getContent().stream().map(Announcement::getId).toList();

		Map<UUID, List<AttachmentResponse>> attachments = loadAttachments(announcementIds);
		// Five constant-cost queries for the whole page instead of per-row lookups.
		Map<UUID, Long> acknowledgementCounts = countsByAnnouncement(
			acknowledgementRepository.countByAnnouncementIdIn(announcementIds));
		Map<UUID, Long> helpfulCounts = countsByAnnouncement(
			helpfulMarkRepository.countByAnnouncementIdIn(announcementIds));
		Set<UUID> acknowledgedByResident = residentInteractions(
			acknowledgementRepository.findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(membership.getId(), announcementIds));
		Set<UUID> helpfulByResident = residentInteractions(
			helpfulMarkRepository.findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(membership.getId(), announcementIds));

		return announcements.map(announcement -> toResidentResponse(
			announcement,
			attachments.getOrDefault(announcement.getId(), List.of()),
			acknowledgedByResident.contains(announcement.getId()),
			helpfulByResident.contains(announcement.getId()),
			acknowledgementCounts.getOrDefault(announcement.getId(), 0L),
			helpfulCounts.getOrDefault(announcement.getId(), 0L)
		));
	}

	@Override
	public ResidentAnnouncementResponse getPublishedAnnouncementForResident(UUID announcementId) {
		UserCommunity membership = resolveResidentActiveMembership();
		Announcement announcement = requireVisibleAnnouncement(announcementId, membership.getCommunityId());

		return toResidentResponse(
			announcement,
			loadAttachments(announcement.getId()),
			acknowledgementRepository.existsByAnnouncementIdAndMembershipId(announcement.getId(), membership.getId()),
			helpfulMarkRepository.existsByAnnouncementIdAndMembershipId(announcement.getId(), membership.getId()),
			acknowledgementRepository.countByAnnouncementId(announcement.getId()),
			helpfulMarkRepository.countByAnnouncementId(announcement.getId())
		);
	}

	@Override
	@Transactional
	public AnnouncementInteractionResponse acknowledgeAnnouncement(UUID announcementId) {
		UserCommunity membership = resolveResidentActiveMembership();
		Announcement announcement = requireVisibleAnnouncement(announcementId, membership.getCommunityId());

		if (!acknowledgementRepository.existsByAnnouncementIdAndMembershipId(announcement.getId(), membership.getId())) {
			try {
				interactionRecorder.insertAcknowledgement(announcement, membership);
			} catch (DataIntegrityViolationException ex) {
				// A concurrent request won the race on the unique constraint; the interaction is
				// already recorded, which is exactly the desired end state.
				log.debug("Duplicate acknowledgement ignored for announcement {}", announcement.getId());
			}
		}

		return buildInteractionResponse(announcement.getId(), membership.getId());
	}

	@Override
	@Transactional
	public AnnouncementInteractionResponse markAnnouncementHelpful(UUID announcementId) {
		UserCommunity membership = resolveResidentActiveMembership();
		Announcement announcement = requireVisibleAnnouncement(announcementId, membership.getCommunityId());

		if (!helpfulMarkRepository.existsByAnnouncementIdAndMembershipId(announcement.getId(), membership.getId())) {
			try {
				interactionRecorder.insertHelpfulMark(announcement, membership);
			} catch (DataIntegrityViolationException ex) {
				log.debug("Duplicate helpful mark ignored for announcement {}", announcement.getId());
			}
		}

		return buildInteractionResponse(announcement.getId(), membership.getId());
	}

	private AnnouncementInteractionResponse buildInteractionResponse(UUID announcementId, UUID membershipId) {
		return AnnouncementInteractionResponse.builder()
			.announcementId(announcementId)
			.acknowledged(acknowledgementRepository.existsByAnnouncementIdAndMembershipId(announcementId, membershipId))
			.helpful(helpfulMarkRepository.existsByAnnouncementIdAndMembershipId(announcementId, membershipId))
			.acknowledgementCount(acknowledgementRepository.countByAnnouncementId(announcementId))
			.helpfulCount(helpfulMarkRepository.countByAnnouncementId(announcementId))
			.build();
	}

	/**
	 * Resolves an announcement by id <em>and</em> community <em>and</em> published state in one
	 * query, so a draft, a soft-deleted row, another community's announcement and a missing one
	 * are externally indistinguishable.
	 */
	private Announcement requireVisibleAnnouncement(UUID announcementId, UUID communityId) {
		if (announcementId == null) {
			throw new ValidationException("Announcement ID is required");
		}

		return announcementRepository
			.findPublishedByIdAndCommunityId(announcementId, communityId)
			.orElseThrow(() -> new NotFoundException("Announcement not found"));
	}

	private Map<UUID, Long> countsByAnnouncement(List<AnnouncementInteractionCount> counts) {
		return counts.stream().collect(Collectors.toMap(
			AnnouncementInteractionCount::getAnnouncementId, AnnouncementInteractionCount::getTotal));
	}

	private Set<UUID> residentInteractions(List<UUID> announcementIds) {
		return Set.copyOf(announcementIds);
	}

	/**
	 * The active community is the one selected on the resident's profile, as established by
	 * {@code UserContextFilter}. It is re-validated against an active membership on every request
	 * so a stale or tampered selection cannot widen access.
	 */
	private UserCommunity resolveResidentActiveMembership() {
		UserContext context = requireContext();

		UUID communityId;
		try {
			communityId = context.communityId() == null ? null : UUID.fromString(context.communityId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("No active community membership");
		}

		if (communityId == null) {
			throw new ForbiddenException("No active community membership");
		}

		return userCommunityRepository
			.findByUserIdAndCommunityIdAndUserStatusActive(parseCurrentUserId(context), communityId)
			.filter(membership -> membership.getRole() != null)
			.orElseThrow(() -> new ForbiddenException("No active community membership"));
	}

	/**
	 * Publication ordering is fixed by the repository query, so any client-supplied sort is
	 * dropped and the page size is capped.
	 */
	private Pageable toResidentPageable(Pageable pageable) {
		if (pageable == null) {
			return PageRequest.of(0, RESIDENT_DEFAULT_PAGE_SIZE);
		}

		int page = Math.max(pageable.getPageNumber(), 0);
		int size = pageable.isUnpaged() ? RESIDENT_DEFAULT_PAGE_SIZE : pageable.getPageSize();
		size = Math.min(Math.max(size, 1), RESIDENT_MAX_PAGE_SIZE);

		return PageRequest.of(page, size);
	}

	private ResidentAnnouncementResponse toResidentResponse(
		Announcement announcement,
		List<AttachmentResponse> attachments,
		boolean acknowledged,
		boolean helpful,
		long acknowledgementCount,
		long helpfulCount
	) {
		return ResidentAnnouncementResponse.builder()
			.id(announcement.getId())
			.title(announcement.getTitle())
			.content(announcement.getContent())
			.category(announcement.getCategory())
			.pinned(announcement.isPinned())
			.publishedAt(announcement.getPublishedAt())
			.acknowledged(acknowledged)
			.helpful(helpful)
			.acknowledgementCount(acknowledgementCount)
			.helpfulCount(helpfulCount)
			.attachments(attachments)
			.build();
	}

	private List<AttachmentResponse> loadAttachments(UUID announcementId) {
		return attachmentReferenceRepository
			.findByOwnerTypeAndOwnerId(AttachmentOwnerType.ANNOUNCEMENT, announcementId)
			.stream()
			.map(this::toAttachmentResponse)
			.toList();
	}

	private Map<UUID, List<AttachmentResponse>> loadAttachments(List<UUID> announcementIds) {
		if (announcementIds.isEmpty()) {
			return Map.of();
		}

		return attachmentReferenceRepository
			.findByOwnerTypeAndOwnerIdIn(AttachmentOwnerType.ANNOUNCEMENT, announcementIds)
			.stream()
			.collect(Collectors.groupingBy(
				AttachmentReference::getOwnerId,
				Collectors.mapping(this::toAttachmentResponse, Collectors.toList())
			));
	}

	private AttachmentResponse toAttachmentResponse(AttachmentReference reference) {
		return AttachmentResponse.builder()
			.id(reference.getAttachment().getId())
			.originalFileName(reference.getAttachment().getOriginalFileName())
			.fileName(reference.getAttachment().getFileName())
			.contentType(reference.getAttachment().getContentType())
			.fileSize(reference.getAttachment().getFileSize())
			.filePath(reference.getAttachment().getFilePath())
			.publicUrl(reference.getAttachment().getFilePath())
			.build();
	}

	private AnnouncementResponse buildAnnouncementResponseWithAttachments(Announcement announcement) {		AnnouncementResponse baseResponse = announcementMapper.toResponse(announcement);

		List<AttachmentResponse> attachments = loadAttachments(announcement.getId());

		return new AnnouncementResponse(
			baseResponse.id(),
			baseResponse.communityId(),
			baseResponse.title(),
			baseResponse.content(),
			baseResponse.category(),
			baseResponse.status(),
			baseResponse.pinned(),
			baseResponse.publishAt(),
			baseResponse.publishedAt(),
			baseResponse.publishedBy(),
			baseResponse.createdAt(),
			baseResponse.updatedAt(),
			baseResponse.createdBy(),
			baseResponse.updatedBy(),
			attachments
		);
	}

	private Pageable applyDefaultSort(Pageable pageable) {
		if (pageable == null) {
			return PageRequest.of(0, 20, DEFAULT_SORT);
		}
		if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
	}

	private Announcement requireAnnouncement(UUID id) {
		return announcementRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Announcement not found with id: " + id));
	}

	private Community requireActiveCommunity(UUID communityId) {
		if (communityId == null) {
			throw new ValidationException("Community ID is required");
		}
		return communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community not found"));
	}

	private String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ValidationException(field + " must not be blank");
		}
		return value;
	}

	private String toNotificationBody(String content) {
		if (content == null) {
			return "";
		}
		String trimmed = content.trim();
		if (trimmed.length() <= 2000) {
			return trimmed;
		}
		return trimmed.substring(0, 1997) + "...";
	}

	/**
	 * Asserts that the caller is authenticated, holds an active membership in the target community
	 * and may manage announcements there.
	 */
	private void requireManageAccess(UUID communityId) {
		UserContext context = requireContext();
		requireActiveMembership(context, communityId);

		if (!context.canManage(Domain.ANNOUNCEMENT, communityId)) {
			throw new ForbiddenException("You are not allowed to manage announcements for this community");
		}
	}

	private User resolveCurrentUser() {
		UUID userId = parseCurrentUserId(requireContext());
		return userRepository.findById(userId)
			.orElseThrow(() -> new ForbiddenException("Authenticated user could not be resolved"));
	}

	private void requireViewAccess(UUID communityId) {
		UserContext context = requireContext();
		requireActiveMembership(context, communityId);

		if (!context.canView(Domain.ANNOUNCEMENT, communityId)) {
			throw new ForbiddenException("You are not allowed to view announcements for this community");
		}
	}

	/**
	 * Membership is resolved from the database, never from the client payload or the community
	 * claim in the token. Platform administrators are not community members and are exempt.
	 */
	private void requireActiveMembership(UserContext context, UUID communityId) {
		if (communityId == null) {
			throw new ForbiddenException("Community scope could not be resolved for this request");
		}

		if (UserContextHolder.isPlatformAdmin()) {
			return;
		}

		UserCommunity membership = userCommunityRepository
			.findByUserIdAndCommunityIdAndUserStatusActive(parseCurrentUserId(context), communityId)
			.orElseThrow(() -> new ForbiddenException("You do not have access to this community"));

		if (membership.getRole() == null) {
			throw new ForbiddenException("User has no role in this community");
		}
	}

	private UserContext requireContext() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}
		return context;
	}

	private UUID parseCurrentUserId(UserContext context) {
		try {
			return UUID.fromString(context.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}
	}

	private void assignCommunity(Announcement announcement, Community community) {
		announcement.setCommunity(community);
		announcement.setCommunityId(community.getId());
	}
}
