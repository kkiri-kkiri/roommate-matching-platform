package com.kkirikkiri.chat.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.kkirikkiri.chat.dto.ChatMessageBroadcastResponse;
import com.kkirikkiri.chat.dto.ChatMessageListRequest;
import com.kkirikkiri.chat.dto.ChatMessageResponse;
import com.kkirikkiri.chat.dto.ChatMessageReadStatusResponse;
import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.dto.ChatReadRequest;
import com.kkirikkiri.chat.dto.ChatRoomCreateRequest;
import com.kkirikkiri.chat.dto.ChatRoomListRequest;
import com.kkirikkiri.chat.dto.ChatRoomPageResponse;
import com.kkirikkiri.chat.dto.ChatRoomCountsResponse;
import com.kkirikkiri.chat.dto.ChatRoomResponse;
import com.kkirikkiri.chat.dto.ChatRoomSettingsRequest;
import com.kkirikkiri.chat.entity.ChatMessage;
import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomCategory;
import com.kkirikkiri.chat.entity.ChatRoomMember;
import com.kkirikkiri.chat.entity.ChatRoomStatus;
import com.kkirikkiri.chat.repository.ChatMessageRepository;
import com.kkirikkiri.chat.repository.ChatRoomMemberRepository;
import com.kkirikkiri.chat.repository.ChatRoomRepository;
import com.kkirikkiri.chat.repository.ChatUnreadCount;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.community.comment.repository.CommunityCommentRepository;
import com.kkirikkiri.community.common.entity.CommunityContentStatus;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.post.repository.CommunityPostRepository;
import com.kkirikkiri.global.exception.CustomException;
import com.kkirikkiri.global.exception.ErrorCode;
import com.kkirikkiri.matching.dto.RoommatePageResponse;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.matching.repository.RoommateMatchRepository;
import com.kkirikkiri.survey.entity.ProfileSurvey;
import com.kkirikkiri.survey.repository.ProfileSurveyRepository;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserStatus;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;

/** userId에는 요청 본문이 아닌 인증된 사용자의 ID를 전달한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatRoomMemberRepository chatRoomMemberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final ProfileSurveyRepository profileSurveyRepository;
	private final RoommateMatchRepository roommateMatchRepository;
	private final CommunityPostRepository communityPostRepository;
	private final CommunityCommentRepository communityCommentRepository;
	private final Validator validator;
	private final PlatformTransactionManager transactionManager;

	/** 나간 방을 제외하고 상대 닉네임으로 검색한다. 고정 여부, 최근 메시지 순으로 반환한다. */
	public ChatRoomPageResponse getRooms(Long userId, ChatRoomListRequest request) {
		validateUserId(userId);
		ChatRoomListRequest filter = request == null ? new ChatRoomListRequest(null, null) : request;
		validateRequest(filter);
		int page = filter.page() == null ? 0 : filter.page();
		int size = filter.size() == null ? ChatRoomListRequest.DEFAULT_SIZE : filter.size();
		if (page < 0 || size < 1 || size > ChatRoomListRequest.MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		String keyword = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
		String pattern = keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
		ChatRoomCategory category = filter.category() == null || "ALL".equals(filter.category())
				? null : ChatRoomCategory.valueOf(filter.category());
		var rows = chatRoomRepository.findRoomPage(userId, category, pattern, PageRequest.of(page, size));
		if (rows.stream().anyMatch(row -> row.memberCount() != 2 || row.counterpartId() == null)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		var targetIds = rows.stream().filter(row -> !row.anonymous()).map(row -> row.counterpartId()).distinct().toList();
		var profiles = targetIds.isEmpty() ? Map.<Long, UserProfile>of()
				: userProfileRepository.findAllByUser_UserIdIn(targetIds).stream()
					.collect(Collectors.toMap(profile -> profile.getUser().getUserId(), Function.identity()));
		var surveys = targetIds.isEmpty() ? Map.<Long, ProfileSurvey>of()
				: profileSurveyRepository.findAllByUser_UserIdIn(targetIds).stream()
					.collect(Collectors.toMap(survey -> survey.getUser().getUserId(), Function.identity()));
		var scores = targetIds.isEmpty() ? Map.<Long, Integer>of()
				: roommateMatchRepository.findAllByRequesterUser_UserIdAndTargetUser_UserIdIn(userId, targetIds).stream()
					.collect(Collectors.toMap(match -> match.getTargetUser().getUserId(), RoommateMatch::getMatchScore));
		var roomIds = rows.stream().map(row -> row.roomId()).toList();
		var unread = roomIds.isEmpty() ? Map.<Long, Long>of()
				: chatMessageRepository.countUnreadForRooms(roomIds, userId).stream()
					.collect(Collectors.toMap(ChatUnreadCount::roomId, ChatUnreadCount::unread));
		var content = rows.map(row -> {
			var counterpart = row.anonymous()
					? new ChatRoomResponse.CounterpartResponse(null, "익명", null, null, null, null, null, null, List.of())
					: toCounterpartResponse(profiles.get(row.counterpartId()), surveys.get(row.counterpartId()), scores.get(row.counterpartId()));
			var last = row.lastSentAt() == null ? null : new ChatRoomResponse.LastMessageResponse(row.lastText(), row.lastSentAt());
			return new ChatRoomResponse(row.roomId(), row.category(), counterpart, row.sourcePost(),
					unread.getOrDefault(row.roomId(), 0L), row.pinned(), row.muted(), last);
		});
		long roommate = 0, community = 0, pinned = 0;
		for (var count : chatRoomRepository.countActiveRoomsByCategory(userId)) {
			if (count.category() == ChatRoomCategory.ROOMMATE) roommate = count.total();
			else if (count.category() == ChatRoomCategory.COMMUNITY) community = count.total();
			pinned += count.pinned();
		}
		return ChatRoomPageResponse.from(content, new ChatRoomCountsResponse(roommate + community, roommate, community, pinned));
	}

	/** A selected/deep-linked room need not be in the currently loaded list page. */
	public ChatRoomResponse getRoom(Long userId, Long roomId) {
		return toRoomResponse(findActiveMember(userId, roomId));
	}

	/**
	 * 방과 두 참여자를 함께 저장한다. UNIQUE 충돌은 생성 트랜잭션이 롤백된 뒤 새 트랜잭션에서 조회한다.
	 * 기존 방의 나간 참여자를 복구할 Entity 메서드가 없으므로 재참여는 허용하지 않는다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ChatRoomResponse createOrGetRoom(Long userId, ChatRoomCreateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		if (request.category() != ChatRoomCategory.COMMUNITY) {
			validateId(request.targetUserId());
			if (userId.equals(request.targetUserId())) {
				throw new CustomException(ErrorCode.INVALID_REQUEST);
			}
		}
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			return transaction.execute(status -> createRoom(userId, request));
		} catch (DataIntegrityViolationException exception) {
			// UNIQUE 충돌 롤백 후 출처 검증과 갱신도 새 트랜잭션에서 수행한다.
			return transaction.execute(status -> createRoom(userId, request, true));
		}
	}

	/** 최신 메시지부터 조회한다. 기본 page=0, size=50이며 읽음 상태는 메시지별 확인 기록이다. */
	public RoommatePageResponse<ChatMessageResponse> getMessages(
			Long userId, Long roomId, ChatMessageListRequest request) {
		findActiveMember(userId, roomId);
		findCounterpart(roomId, userId);
		ChatMessageListRequest filter = request == null ? new ChatMessageListRequest(null, null, null) : request;
		int page = filter.page() == null ? 0 : filter.page();
		validateRequest(filter);
		int size = filter.size() == null ? ChatMessageListRequest.DEFAULT_SIZE : filter.size();
		if (page < 0 || size < 1 || size > ChatMessageListRequest.MAX_SIZE || (long) page * size > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		PageRequest pageable = PageRequest.of(page, size, Sort.by(filter.afterId() == null
				? Sort.Direction.DESC : Sort.Direction.ASC, "sentAt", "messageId"));
		String keyword = filter.q() == null ? "" : filter.q().trim();
		org.springframework.data.domain.Page<ChatMessage> messages;
		Long cursor = filter.beforeId() == null ? filter.afterId() : filter.beforeId();
		if (cursor == null) {
			messages = keyword.isEmpty() ? chatMessageRepository.findAllByChatRoom_ChatRoomId(roomId, pageable)
					: chatMessageRepository.findAllByChatRoom_ChatRoomIdAndContentContaining(roomId, keyword, pageable);
		} else {
			ChatMessage anchor = chatMessageRepository.findByMessageIdAndChatRoom_ChatRoomId(cursor, roomId)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
			String pattern = keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
			messages = filter.beforeId() != null
					? chatMessageRepository.findBefore(roomId, anchor.getSentAt(), cursor, pattern, pageable)
					: chatMessageRepository.findAfter(roomId, anchor.getSentAt(), cursor, pattern, pageable);
		}
		return RoommatePageResponse.from(messages.map(message -> {
			boolean mine = userId.equals(message.getSender().getUserId());
			return ChatMessageResponse.from(message, mine ? "ME" : "OTHER", message.isRead());
		}));
	}

	/** Read-only polling: expose no sender/profile data and never acknowledge a message. */
	public List<ChatMessageReadStatusResponse> getReadStatuses(Long userId, Long roomId, ChatReadRequest request) {
		findActiveMember(userId, roomId);
		validateRequest(request);
		var ids = request.messageIds().stream().distinct().toList();
		var statuses = chatMessageRepository.findReadStatuses(roomId, ids);
		if (statuses.size() != ids.size()) throw new CustomException(ErrorCode.INVALID_REQUEST);
		return statuses;
	}

	/** 저장 성공 후 반환한 DTO를 WebSocket Controller에서 구독자에게 전달한다. */
	@Transactional
	public ChatMessageBroadcastResponse sendMessage(Long userId, Long roomId, ChatMessageSendRequest request) {
		validateRequest(request);
		ChatRoomMember member = findActiveMember(userId, roomId);
		findCounterpart(roomId, userId);
		ChatMessage message = chatMessageRepository.save(
				new ChatMessage(member.getChatRoom(), member.getUser(), request.text()));
		return ChatMessageBroadcastResponse.from(message, message.isRead());
	}

	@Transactional
	public void markRead(Long userId, Long roomId, ChatReadRequest request) {
		validateRequest(request);
		ChatRoomMember member = findActiveMemberForUpdate(userId, roomId);
		var ids = request.messageIds().stream().distinct().sorted().toList();
		var incoming = chatMessageRepository.findIncomingForRead(roomId, userId, ids);
		if (incoming.size() != ids.size()) throw new CustomException(ErrorCode.INVALID_REQUEST);
		incoming.forEach(ChatMessage::markRead);
		LocalDateTime latest = incoming.stream().map(ChatMessage::getSentAt).max(Comparator.naturalOrder()).orElseThrow();
		chatRoomMemberRepository.advanceReadTime(member.getChatRoomMemberId(), latest);
		// Keep the managed entity in sync with the conditional update, including reentrant calls.
		member.markRead(latest);
	}

	/** Acquire the shared account lock before any membership/count snapshot. */
	@Transactional
	public void updateSettings(Long userId, Long roomId, ChatRoomSettingsRequest request) {
		validateRequest(request);
		ChatRoomMember member = findActiveMemberForUpdate(userId, roomId);
		if (Boolean.TRUE.equals(request.pinned()) && !member.isPinned()
				&& chatRoomMemberRepository.findPinnedMembersForUpdate(userId).size() >= 2) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		member.changeSettings(request.pinned(), request.muted());
	}

	@Transactional
	public void leaveRoom(Long userId, Long roomId) {
		findActiveMemberForUpdate(userId, roomId).leave();
	}

	private ChatRoomResponse createRoom(Long userId, ChatRoomCreateRequest request) {
		return createRoom(userId, request, false);
	}

	private ChatRoomResponse createRoom(Long userId, ChatRoomCreateRequest request, boolean recovery) {
		User user = findUser(userId, ErrorCode.UNAUTHORIZED);
		User target = request.category() == ChatRoomCategory.COMMUNITY
				? null : findUser(request.targetUserId(), ErrorCode.INVALID_REQUEST);
		CommunityPost post = null;
		CommunityComment comment = null;
		if (request.category() == ChatRoomCategory.ROOMMATE) {
			if (request.sourceFrom() != null || request.sourcePostId() != null || request.sourceCommentId() != null) {
				throw new CustomException(ErrorCode.VALIDATION_ERROR);
			}
		} else {
			validateId(request.sourcePostId());
			post = communityPostRepository.findByPostIdAndStatus(request.sourcePostId(), CommunityContentStatus.ACTIVE)
					.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
			if ("POST".equals(request.sourceFrom())) {
				if (request.sourceCommentId() != null) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
				// 익명 작성자 ID를 추측하는 검증 수단이 되지 않도록 전달된 ID는 무시한다.
				if (!post.isAnonymous() && request.targetUserId() != null
						&& !request.targetUserId().equals(post.getAuthor().getUserId())) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
				target = findUser(post.getAuthor().getUserId(), ErrorCode.INVALID_REQUEST);
			} else if ("COMMENT".equals(request.sourceFrom())) {
				validateId(request.sourceCommentId());
				comment = communityCommentRepository.findByCommentIdAndStatus(
						request.sourceCommentId(), CommunityContentStatus.ACTIVE)
						.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
				if (!post.getPostId().equals(comment.getPost().getPostId())) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
				// 익명 출처에서는 전달된 ID를 비교하지 않아 작성자 ID 추측에 대한 응답 차이를 없앤다.
				if (!comment.isAnonymous() && request.targetUserId() != null
						&& !request.targetUserId().equals(comment.getAuthor().getUserId())) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
				target = findUser(comment.getAuthor().getUserId(), ErrorCode.INVALID_REQUEST);
				post = null;
			} else {
				throw new CustomException(ErrorCode.VALIDATION_ERROR);
			}
		}
		if (userId.equals(target.getUserId())) throw new CustomException(ErrorCode.INVALID_REQUEST);
		var existing = chatRoomRepository.findByRoomKey(
				ChatRoom.createRoomKey(userId, target.getUserId(), request.category(), post, comment));
		if (existing.isPresent()) {
			ChatRoomMember activeMember = findActiveMember(userId, existing.get().getChatRoomId());
			if (post != null && !post.isAnonymous()) existing.get().updateSourcePost(post);
			return toRoomResponse(activeMember);
		}
		if (recovery) throw new CustomException(ErrorCode.INVALID_REQUEST);
		ChatRoom room = chatRoomRepository.save(new ChatRoom(userId, target.getUserId(), request.category(), post, comment));
		ChatRoomMember member = new ChatRoomMember(room, user);
		chatRoomMemberRepository.saveAll(List.of(member, new ChatRoomMember(room, target)));
		return toRoomResponse(member);
	}

	private ChatRoomResponse toRoomResponse(ChatRoomMember member) {
		ChatRoom room = member.getChatRoom();
		Long roomId = room.getChatRoomId();
		Long userId = member.getUser().getUserId();
		Long targetId = findCounterpart(roomId, userId).getUser().getUserId();
		var counterpart = room.isAnonymousAuthor(targetId)
				? new ChatRoomResponse.CounterpartResponse(null, "익명", null, null, null, null, null, null, List.of())
				: toCounterpartResponse(userId, targetId);
		Long sourcePost = room.getSourcePost() != null ? room.getSourcePost().getPostId()
				: room.getSourceComment() == null ? null : room.getSourceComment().getPost().getPostId();
		var lastMessage = chatMessageRepository.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(roomId)
				.map(message -> new ChatRoomResponse.LastMessageResponse(message.getContent(), message.getSentAt()))
				.orElse(null);
		return ChatRoomResponse.from(room, counterpart, sourcePost,
				chatMessageRepository.countUnreadMessages(roomId, userId),
				member.isPinned(), member.isMuted(), lastMessage);
	}

	/** 해당 방의 현재 활성 참여자가 아니면 기존 권한 예외를 발생시킨다. */
	public void validateActiveMember(Long userId, Long roomId) {
		findActiveMember(userId, roomId);
	}

	private ChatRoomResponse.CounterpartResponse toCounterpartResponse(Long userId, Long targetId) {
		UserProfile profile = userProfileRepository.findByUser_UserId(targetId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		// RoommateMatchingService의 기존 태그 규칙: 흡연, 취침 시간, 청소 빈도 순서.
		// 해당 생성 메서드는 private이므로 동일한 기존 규칙을 유지한다.
		ProfileSurvey survey = profileSurveyRepository.findByUser_UserId(targetId).orElse(null);
		Integer score = roommateMatchRepository.findByRequesterUser_UserIdAndTargetUser_UserId(userId, targetId)
				.map(RoommateMatch::getMatchScore).orElse(null);
		return toCounterpartResponse(profile, survey, score);
	}

	private ChatRoomResponse.CounterpartResponse toCounterpartResponse(UserProfile profile, ProfileSurvey survey, Integer score) {
		if (profile == null) throw new CustomException(ErrorCode.INVALID_REQUEST);
		List<String> tags = survey == null || !survey.isCompleted() ? List.of()
				: Stream.of(survey.getSmoking(), survey.getSleepTime(), survey.getCleaningFrequency()).filter(Objects::nonNull).toList();
		return new ChatRoomResponse.CounterpartResponse(profile.getUser().getUserId(), profile.getNickname(),
				profile.getProfileImageUrl(),
				profile.getDormitory() == null ? null : profile.getDormitory().getDormitoryName(), profile.getAdmissionYear(),
				profile.getMajor() == null ? null : profile.getMajor().getMajorName(), score, profile.getIntroduction(), tags);
	}

	private ChatRoomMember findActiveMember(Long userId, Long roomId) {
		validateUserId(userId);
		validateId(roomId);
		return chatRoomMemberRepository.findByChatRoom_ChatRoomIdAndUser_UserId(roomId, userId)
				.filter(member -> !member.isLeft() && member.getChatRoom().getStatus() == ChatRoomStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
	}

	private ChatRoomMember findCounterpart(Long roomId, Long userId) {
		List<ChatRoomMember> members = chatRoomMemberRepository.findAllByChatRoom_ChatRoomId(roomId);
		if (members.size() != 2 || members.stream().noneMatch(member -> userId.equals(member.getUser().getUserId()))) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return members.stream().filter(member -> !userId.equals(member.getUser().getUserId())).findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}

	/** All membership writes: USER -> membership -> incoming message IDs in ascending order. */
	private ChatRoomMember findActiveMemberForUpdate(Long userId, Long roomId) {
		validateUserId(userId);
		validateId(roomId);
		userRepository.findByIdForAccountUpdate(userId)
				.filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		return chatRoomMemberRepository.findMemberForUpdate(roomId, userId)
				.filter(member -> !member.isLeft() && member.getChatRoom().getStatus() == ChatRoomStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(ErrorCode.FORBIDDEN));
	}

	private User findUser(Long userId, ErrorCode errorCode) {
		return userRepository.findById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
				.orElseThrow(() -> new CustomException(errorCode));
	}

	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw new CustomException(ErrorCode.UNAUTHORIZED);
		}
	}

	private void validateId(Long id) {
		if (id == null || id <= 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	private void validateRequest(Object request) {
		if (request == null || !validator.validate(request).isEmpty()) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
