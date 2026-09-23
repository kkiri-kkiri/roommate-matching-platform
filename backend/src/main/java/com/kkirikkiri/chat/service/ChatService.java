package com.kkirikkiri.chat.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
import com.kkirikkiri.chat.dto.ChatMessageSendRequest;
import com.kkirikkiri.chat.dto.ChatRoomCreateRequest;
import com.kkirikkiri.chat.dto.ChatRoomListRequest;
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
	public List<ChatRoomResponse> getRooms(Long userId, ChatRoomListRequest request) {
		validateUserId(userId);
		ChatRoomListRequest filter = request == null ? new ChatRoomListRequest(null, null) : request;
		validateRequest(filter);
		List<ChatRoomMember> members = filter.category() == null || "ALL".equals(filter.category())
				? chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalse(userId)
				: chatRoomMemberRepository.findAllByUser_UserIdAndLeftFalseAndChatRoom_Category(
						userId, ChatRoomCategory.valueOf(filter.category()));
		String keyword = filter.q() == null ? "" : filter.q().trim().toLowerCase(Locale.ROOT);
		return members.stream()
				.filter(member -> member.getChatRoom().getStatus() == ChatRoomStatus.ACTIVE)
				.map(this::toRoomResponse)
				.filter(room -> keyword.isEmpty()
						|| room.counterpart().nickname().toLowerCase(Locale.ROOT).contains(keyword))
				.sorted(Comparator.comparing(ChatRoomResponse::pinned).reversed()
						.thenComparing(room -> room.lastMessage() == null ? null : room.lastMessage().sentAt(),
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ChatRoomResponse::id, Comparator.reverseOrder()))
				.toList();
	}

	/**
	 * 방과 두 참여자를 함께 저장한다. UNIQUE 충돌은 생성 트랜잭션이 롤백된 뒤 새 트랜잭션에서 조회한다.
	 * 기존 방의 나간 참여자를 복구할 Entity 메서드가 없으므로 재참여는 허용하지 않는다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ChatRoomResponse createOrGetRoom(Long userId, ChatRoomCreateRequest request) {
		validateUserId(userId);
		validateRequest(request);
		validateId(request.targetUserId());
		if (userId.equals(request.targetUserId())) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			return transaction.execute(status -> createRoom(userId, request));
		} catch (DataIntegrityViolationException exception) {
			transaction.setReadOnly(true);
			return transaction.execute(status -> {
				ChatRoom room = chatRoomRepository.findByRoomKey(ChatRoom.createRoomKey(
						userId, request.targetUserId(), request.category()))
						.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
				return toRoomResponse(findActiveMember(userId, room.getChatRoomId()));
			});
		}
	}

	/** 최신 메시지부터 조회한다. 기본 page=0, size=50이며 읽음 상태는 수신자의 기준 시각으로 판단한다. */
	public RoommatePageResponse<ChatMessageResponse> getMessages(
			Long userId, Long roomId, ChatMessageListRequest request) {
		ChatRoomMember member = findActiveMember(userId, roomId);
		ChatRoomMember counterpart = findCounterpart(roomId, userId);
		ChatMessageListRequest filter = request == null ? new ChatMessageListRequest(null, null, null) : request;
		int page = filter.page() == null ? 0 : filter.page();
		int size = filter.size() == null ? 50 : filter.size();
		if (page < 0 || size < 1 || (long) page * size > Integer.MAX_VALUE) {
			throw new CustomException(ErrorCode.VALIDATION_ERROR);
		}
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt", "messageId"));
		String keyword = filter.q() == null ? "" : filter.q().trim();
		var messages = keyword.isEmpty()
				? chatMessageRepository.findAllByChatRoom_ChatRoomId(roomId, pageable)
				: chatMessageRepository.findAllByChatRoom_ChatRoomIdAndContentContaining(roomId, keyword, pageable);
		return RoommatePageResponse.from(messages.map(message -> {
			boolean mine = userId.equals(message.getSender().getUserId());
			return ChatMessageResponse.from(message, mine ? "ME" : "OTHER",
					isRead(message, mine ? counterpart : member));
		}));
	}

	/** 저장 성공 후 반환한 DTO를 WebSocket Controller에서 구독자에게 전달한다. */
	@Transactional
	public ChatMessageBroadcastResponse sendMessage(Long userId, Long roomId, ChatMessageSendRequest request) {
		validateRequest(request);
		ChatRoomMember member = findActiveMember(userId, roomId);
		ChatRoomMember counterpart = findCounterpart(roomId, userId);
		ChatMessage message = chatMessageRepository.save(
				new ChatMessage(member.getChatRoom(), member.getUser(), request.text()));
		return ChatMessageBroadcastResponse.from(message, isRead(message, counterpart));
	}

	@Transactional
	public void markRead(Long userId, Long roomId) {
		ChatRoomMember member = findActiveMember(userId, roomId);
		chatMessageRepository.findFirstByChatRoom_ChatRoomIdAndSender_UserIdNotOrderBySentAtDescMessageIdDesc(
				roomId, userId).ifPresent(message -> member.markRead(message.getSentAt()));
	}

	/** 사용자 단위 잠금 조회가 없어 동시 고정 요청의 최대 2개 보장은 선행 Repository 보완이 필요하다. */
	@Transactional
	public void updateSettings(Long userId, Long roomId, ChatRoomSettingsRequest request) {
		validateRequest(request);
		ChatRoomMember member = findActiveMember(userId, roomId);
		if (Boolean.TRUE.equals(request.pinned()) && !member.isPinned()
				&& chatRoomMemberRepository.countByUser_UserIdAndPinnedTrueAndLeftFalse(userId) >= 2) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		member.changeSettings(request.pinned(), request.muted());
	}

	@Transactional
	public void leaveRoom(Long userId, Long roomId) {
		findActiveMember(userId, roomId).leave();
	}

	private ChatRoomResponse createRoom(Long userId, ChatRoomCreateRequest request) {
		User user = findUser(userId, ErrorCode.UNAUTHORIZED);
		User target = findUser(request.targetUserId(), ErrorCode.INVALID_REQUEST);
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
				if (request.sourceCommentId() != null || !target.getUserId().equals(post.getAuthor().getUserId())) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
			} else if ("COMMENT".equals(request.sourceFrom())) {
				validateId(request.sourceCommentId());
				comment = communityCommentRepository.findByCommentIdAndStatus(
						request.sourceCommentId(), CommunityContentStatus.ACTIVE)
						.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
				if (!post.getPostId().equals(comment.getPost().getPostId())
						|| !target.getUserId().equals(comment.getAuthor().getUserId())) {
					throw new CustomException(ErrorCode.INVALID_REQUEST);
				}
				post = null;
			} else {
				throw new CustomException(ErrorCode.VALIDATION_ERROR);
			}
		}
		var existing = chatRoomRepository.findByRoomKey(
				ChatRoom.createRoomKey(userId, target.getUserId(), request.category()));
		if (existing.isPresent()) {
			return toRoomResponse(findActiveMember(userId, existing.get().getChatRoomId()));
		}
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
		UserProfile profile = userProfileRepository.findByUser_UserId(targetId)
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
		// RoommateMatchingService의 기존 태그 규칙: 흡연, 취침 시간, 청소 빈도 순서.
		// 해당 생성 메서드는 private이므로 동일한 기존 규칙을 유지한다.
		List<String> tags = profileSurveyRepository.findByUser_UserId(targetId).filter(ProfileSurvey::isCompleted)
				.map(survey -> Stream.of(survey.getSmoking(), survey.getSleepTime(), survey.getCleaningFrequency())
						.filter(Objects::nonNull).toList()).orElse(List.of());
		Integer score = roommateMatchRepository.findByRequesterUser_UserIdAndTargetUser_UserId(userId, targetId)
				.map(RoommateMatch::getMatchScore).orElse(null);
		var counterpart = new ChatRoomResponse.CounterpartResponse(targetId, profile.getNickname(),
				profile.getProfileImageUrl(),
				profile.getDormitory() == null ? null : profile.getDormitory().getDormitoryName(), profile.getAdmissionYear(),
				profile.getMajor() == null ? null : profile.getMajor().getMajorName(), score, profile.getIntroduction(), tags);
		Long sourcePost = room.getSourcePost() != null ? room.getSourcePost().getPostId()
				: room.getSourceComment() == null ? null : room.getSourceComment().getPost().getPostId();
		var lastMessage = chatMessageRepository.findFirstByChatRoom_ChatRoomIdOrderBySentAtDescMessageIdDesc(roomId)
				.map(message -> new ChatRoomResponse.LastMessageResponse(message.getContent(), message.getSentAt()))
				.orElse(null);
		return ChatRoomResponse.from(room, counterpart, sourcePost,
				chatMessageRepository.countUnreadMessages(roomId, userId, member.getLastReadAt()),
				member.isPinned(), member.isMuted(), lastMessage);
	}

	/** 해당 방의 현재 활성 참여자가 아니면 기존 권한 예외를 발생시킨다. */
	public void validateActiveMember(Long userId, Long roomId) {
		findActiveMember(userId, roomId);
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

	private boolean isRead(ChatMessage message, ChatRoomMember recipient) {
		LocalDateTime lastReadAt = recipient.getLastReadAt();
		return lastReadAt != null && !message.getSentAt().isAfter(lastReadAt);
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
