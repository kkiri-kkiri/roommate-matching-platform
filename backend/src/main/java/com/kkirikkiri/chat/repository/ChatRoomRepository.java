package com.kkirikkiri.chat.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.chat.entity.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

	/**
	 * 사용자 쌍 + category 키로 조회한다. 익명 출처는 POST/COMMENT ID까지 포함한다.
	 * 동시 생성 시 UNIQUE 충돌과 기존 방 재조회는 서비스에서 처리한다.
	 */
	Optional<ChatRoom> findByRoomKey(String roomKey);
}
