package com.kkirikkiri.chat.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kkirikkiri.chat.entity.ChatRoom;
import com.kkirikkiri.chat.entity.ChatRoomCategory;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	String ANONYMOUS = """
			((locate(':POST:', r.roomKey) > 0 and sourcePost.author.userId = counterpart.user.userId)
			 or (locate(':COMMENT:', r.roomKey) > 0 and sourceComment.author.userId = counterpart.user.userId))
			""";
	String ROOM_JOINS = """
			 from ChatRoomMember member join member.chatRoom r
			 left join ChatRoomMember counterpart on counterpart.chatRoom = r and counterpart.user.userId <> :userId
			 left join r.sourcePost sourcePost
			 left join r.sourceComment sourceComment
			 left join sourceComment.post commentPost
			 left join UserProfile profile on profile.user.userId = counterpart.user.userId
			""";
	String ROOM_FILTER = """
			 where member.user.userId = :userId and member.left = false and r.status = 'ACTIVE'
			 and (:category is null or r.category = :category)
			 and (:pattern is null or lower(case when
			""" + ANONYMOUS + """
			 then '익명' else profile.nickname end) like :pattern escape '!')
			""";

	@Query(value = """
			select new com.kkirikkiri.chat.repository.ChatRoomListRow(
			r.chatRoomId, r.category, counterpart.user.userId, coalesce(sourcePost.postId, commentPost.postId),
			case when
			""" + ANONYMOUS + """
			 then true else false end, member.pinned, member.muted, latest.content, latest.sentAt,
			(select count(allMembers) from ChatRoomMember allMembers where allMembers.chatRoom = r))
			""" + ROOM_JOINS + """
			 left join ChatMessage latest on latest.chatRoom = r and not exists (
			   select newer.messageId from ChatMessage newer where newer.chatRoom = r
			   and (newer.sentAt > latest.sentAt or (newer.sentAt = latest.sentAt and newer.messageId > latest.messageId)))
			""" + ROOM_FILTER + """
			 order by member.pinned desc, case when latest.sentAt is null then 1 else 0 end,
			 latest.sentAt desc, r.chatRoomId desc
			""",
			countQuery = "select count(distinct r.chatRoomId)" + ROOM_JOINS + ROOM_FILTER)
	Page<ChatRoomListRow> findRoomPage(@Param("userId") Long userId, @Param("category") ChatRoomCategory category,
			@Param("pattern") String pattern, Pageable pageable);

	@Query("""
			select new com.kkirikkiri.chat.repository.ChatRoomCategoryCount(r.category, count(member),
			 sum(case when member.pinned = true then 1 else 0 end))
			from ChatRoomMember member join member.chatRoom r
			where member.user.userId = :userId and member.left = false and r.status = 'ACTIVE'
			group by r.category
			""")
	List<ChatRoomCategoryCount> countActiveRoomsByCategory(@Param("userId") Long userId);

	/**
	 * 사용자 쌍 + category 키로 조회한다. 익명 출처는 POST/COMMENT ID까지 포함한다.
	 * 동시 생성 시 UNIQUE 충돌과 기존 방 재조회는 서비스에서 처리한다.
	 */
	Optional<ChatRoom> findByRoomKey(String roomKey);
}
