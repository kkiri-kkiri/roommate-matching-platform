/**
 * 관리자 API 13.1~13.16과 AdminPage.js의 표시 필드를 기준으로 한 요청/응답 계약.
 *
 * <p>공통 성공 응답은 기존 global.response.ApiResponse를 사용한다.
 * 페이징은 API 1.5와 동일한 기존 matching.dto.RoommatePageResponse에 응답 DTO로 변환한
 * 페이지를 전달하여 재사용한다. Entity 페이지를 직접 전달하지 않는다.
 *
 * <p>회원/게시글/신고 목록은 각각 AdminMemberResponse, AdminPostResponse,
 * AdminReportResponse를 사용한다. 게시글 상세는 목록 필드에 content를 추가한
 * AdminPostDetailResponse를 사용한다. 댓글 목록/상세는 AdminCommentResponse,
 * 수집 일정 목록/상세는 AdminCrawledScheduleResponse를 재사용한다.
 * 대시보드는 구체적인 중첩 Summary 타입을 사용하며 학교/기숙사 일정 요약 타입도 공유한다.
 *
 * <p>프론트 Mock의 email/dorm은 API의 schoolEmail/dormitory로 통일한다.
 * authorId/reporterId/targetUserId로 Mock 회원을 찾던 화면에는 author/reporter/targetUser
 * 객체를 제공한다. 목록 사용자 요약은 CommunityAuthorResponse를 재사용하고,
 * 신고 상세만 schoolEmail을 포함한다. 회원 status는 대상자 정보에만 추가한다.
 * 프론트 연동 시 한국어 상태/유형/분류 표시값은 API 코드와 매핑해야 한다.
 * 회원 상세 Mock의 studentId는 실제 학번이지만 API/Entity는 admissionYear만 제공하므로
 * 전체 학번을 임의로 생성하지 않는다.
 *
 * <p>서비스 조립 기준:
 * 회원 Tuple의 member와 nullable profile을 분리하여 가입일과 프로필 필드를 매핑한다.
 * 프로필 없는 회원의 닉네임/생활관/학적 정보는 null일 수 있다.
 * 작성자/신고자/대상자 닉네임은 AdminMemberQueryRepository.findProfilesByUserIds로 보완한다.
 * likes는 CommunityPostReadRepository.countLikes, commentCount는
 * CommunityCommentRepository.countByPost_PostIdAndStatus의 집계 결과를 사용한다.
 * reported는 현재 관리자 본인의 신고 여부가 아니라 해당 대상에 대한 전체 신고 존재 여부다.
 * 기존 관리자 조회의 reported 필터는 이 의미를 지원하지만 응답 항목에 boolean을 제공하지 않는다.
 * 전체 신고 존재 여부 전용 조회는 없어 서비스 단계에서 기존 조회의 조합 또는 조회 보완을 검토한다.
 * CommunityPostReadRepository.isReported(postId, userId)는 이 용도로 그대로 사용하지 않는다.
 * 신고 targetId/targetContent, 활동 집계, 대시보드 통계와 크롤링 실행/완료/실패 이력도
 * 서비스에서 조합하여 record 생성자로 전달한다.
 *
 * <p>일정 화면은 목록/상세 모두 date만 사용하므로 CalendarEvent.startDate를 매핑한다.
 * Entity의 endDate는 현재 화면/API 계약으로 표현되지 않는다. 기간 표시가 필요해지면
 * 프론트와 API를 함께 확정해야 하며 DTO에 endDate/sourceUrl 등을 선제 추가하지 않는다.
 *
 * <p>상세/삭제 URL의 식별자는 경로 변수이므로 별도 요청 본문 DTO를 만들지 않는다.
 * 13.4/13.7/13.10/13.13에는 별도 응답 필드가 없으며, 데이터 없는 응답에는
 * API 1.3의 공통 성공 응답(data=null)을 재사용한다.
 */
package com.kkirikkiri.admin.dto;
