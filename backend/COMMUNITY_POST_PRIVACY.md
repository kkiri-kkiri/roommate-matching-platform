# 커뮤니티 3번: 익명 게시글 작성자 ID 보호

2026-10-19 요청 범위: 익명 게시글 응답, 본인 여부, 출처 기반 채팅 연결.
기존 1·2·4·5번의 잠금·계정 상태 검증·부모 삭제·이미지 정책은 유지한다.

## 원인과 변경

- `CommunityAuthorResponse.forPost`가 익명 닉네임·전공을 가리면서 실제 ID는 유지했다.
  목록·검색·내 게시글·상세가 모두 이 경로를 사용한다. 이제 익명이면
  `author={id:null,nickname:"익명",major:null}`을 반환한다. 일반 응답과 관리자 전용 응답은 유지한다.
- 게시글 목록·상세에 `mine`을 추가했다. Controller가 Principal에서 얻은 조회자 ID와
  Entity의 실제 작성자를 비교한다. 게스트는 false다. 프론트의 내 글 배지·관리 버튼·수정 경로는
  `mine`을 사용한다. 수정·삭제 서비스의 기존 잠금 및 내부 작성자 검증은 변경하지 않는다.
- COMMUNITY의 POST도 COMMENT와 같이 `targetUserId` 없이 출처로 작성자를 찾는다.
  일반 출처에 전달한 ID는 일치를 검증한다. 익명 출처에서는 해당 ID를 무시한다.
  잘못된 ID를 거절하는 방식은 ID 추측의 성공/실패 판별 수단이 되므로 사용하지 않는다.
  대상은 항상 서버가 읽은 출처 작성자로 고정되고, 자기 자신·비활성 사용자·잘못된 출처는 거절한다.
- 익명 채팅방의 상대 프로필 마스킹, REST/WebSocket 발신자 ID 마스킹,
  ROOMMATE/COMMUNITY 분리 및 POST/COMMENT별 익명 키는 기존 구현을 재사용한다.
  외부 방 ID는 숫자 PK이며 사용자 ID가 포함된 내부 `room_key`나 참여자 Entity를 반환하지 않는다.
- 댓글·답글의 게시글별 번호, 소프트 삭제 후 번호 유지, `mine`과 소유권 검증은 유지한다.
  익명 게시글은 실제 ID가 없으므로 일반 댓글 작성자와 연결하는 작성자 배지도 표시하지 않는다.
- 프로필 API는 사용자 ID에 대한 공개 프로필만 제공하며 익명 출처를 해석하는 별도 프로필 경로는 없다.
  커뮤니티 작성자 이름에는 기존에도 프로필 링크가 없었다. 채팅의 일반 상대 프로필 UI와
  익명 상대 프로필 열기 차단, 기존 익명 한경이 이미지를 유지한다.

## 호환성과 배포

- 스키마·운영 설정 변경 및 새 마이그레이션은 없다. DB에는 실제 작성자 관계를 유지한다.
- `author.id`의 nullable 계약과 게시글 `mine`을 지원하는 프론트와 백엔드를 함께 배포한다.
  구버전 프론트는 익명 글의 관리 버튼·채팅 연결이 깨질 수 있다.
- 기존 익명 방의 키를 적용하지 않은 DB는 기존
  [수동 마이그레이션 안내](migrations/README.md)와 [ERD 검증 기준](ERD.md)을 따라 별도로 확인한다.
  이번 작업은 운영·공유 DB나 기존 데이터를 변경하지 않는다.

## 추가 확정 정책: 작성 후 익명 여부 변경 금지

최초 작성 시 일반·익명 중 선택한 값을 게시글이 유지되는 동안 고정한다.
기존 게시글은 배포 당시 저장된 값을 그대로 기준으로 사용한다. 최초 값의 과거 변경 이력을
복구하거나 데이터·채팅방을 일괄 변경하지 않는다.

| 수정 request의 anonymous | 처리 |
| --- | --- |
| 생략 | 저장된 값 유지 |
| 명시적 null | 저장된 값 유지: 기존 nullable Boolean 계약을 재사용 |
| 저장값과 같은 true/false | 호환성을 위해 허용 |
| 저장값과 다른 true/false | 400 / VALIDATION_ERROR, 전체 요청 거부 |

- Service는 기존 계정 → 게시글 잠금·refresh·ACTIVE·작성자 검증 후 저장값을 비교한다.
  거부는 제목·본문 변경, 이미지 연결 제거·순서 변경, 신규 파일 저장보다 앞서 수행한다.
  잠금 대기 이후에도 최신 저장값으로 검증하며 별도 클라이언트의 이전 상태는 신뢰하지 않는다.
- Entity는 생성자에서만 익명 여부를 지정한다. 일반 기본 생성자 오버로드는 유지하고,
  공개 `changeAnonymous` 메서드를 제거하여 내부 서비스도 사후 변경 경로를 사용하지 못한다.
  생성자는 DB 매핑·DDL을 변경하지 않는다. 댓글·대댓글의 정책은 변경하지 않았다.
- 생성 화면은 기존 체크박스를 유지한다. 수정 화면은 기존 상태를 비활성 체크박스로 표시하며
  변경 불가 이유를 안내한다. Form과 상위 Page 모두 수정 payload에서 anonymous를 제외한다.
  제목·본문·이미지 유지 ID·신규 파일과 기존 저장 성공/실패 흐름은 유지한다.
- 경로 감사 결과 사용자 수정은 단일 PATCH `/api/posts/{postId}`이고 이미지 수정도 같은
  서비스 경로다. 관리자 게시글 기능은 조회·삭제이며 별도 익명 상태 수정/PUT 경로가 없다.
  신규 테스트는 사용자 PUT·관리자 PATCH로의 우회도 4xx로 거부되는지 검증한다.
- 기존 채팅 단위 테스트 중 과거 전환 데이터의 방 익명성 유지는 테스트 전용 reflection으로
  과거 상태를 재현한다. 신규 통합 테스트의 legacy fixture SQL도 격리된 H2에만 적용한다.

### 이번 추가 작업의 변경 파일

| 파일 | 주요 변경 |
| --- | --- |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityPostUpdateRequest.java` | 생략/null/동일/상이 값 계약 명시 |
| `backend/src/main/java/com/kkirikkiri/community/post/entity/CommunityPost.java` | 생성자 익명 초기화, 사후 변경 메서드 제거 |
| `backend/src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java` | 잠금 후 변경 거부, 생성자 초기화 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityPostAnonymityPolicyIntegrationTest.java` | 신규: multipart JSON·파일·동시성·legacy 회귀 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java` | 기존 변경 허용 테스트를 거부 정책으로 갱신, 생성자 fixture |
| `backend/src/test/java/com/kkirikkiri/community/post/service/PostLikeListIntegrationTest.java` | 생성자에서 익명 fixture 초기화 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/PostNullableDormitoryIntegrationTest.java` | 생성자에서 익명 fixture 초기화 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityPostPrivacyIntegrationTest.java` | 기존 24개 검증을 유지하며 생성자 fixture 적용 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityContentConcurrencyIntegrationTest.java` | 기존 47개 중 롤백 검증의 요청을 동일 익명 값으로 조정, 본문·이미지 실제 롤백 유지 |
| `backend/src/test/java/com/kkirikkiri/chat/service/ChatServiceTest.java` | 생성자 fixture 및 과거 전환 상태 재현 |
| `frontend/src/pages/community/CommunityPostFormPage.js` | 수정 체크박스 잠금, 수정 시 anonymous 제외 |
| `frontend/src/pages/community/CommunityPage.js` | 수정 payload에서 anonymous 제외 |
| `frontend/src/pages/community/CommunityPage.test.js` | 읽기 전용 상태·재진입·저장 실패·생성 선택 검증 |
| `backend/API.md`, `backend/ERD.md`, `backend/DEPLOYMENT.md`, `backend/COMMUNITY_POST_PRIVACY.md` | 확정 계약·배포·잔여 위험·검증 안내 |

### 추가 정책 검증 결과 (2026-10-19)

작업 결과: 완료. 이번 추가 작업은 위 17개 파일에 해당하며 이전 미커밋 변경도 보존했다.

| 검증 | 최종 결과 |
| --- | --- |
| `backend/gradlew.bat test bootJar` | 성공, 975개 통과: 기존 952개 + 신규 23개 |
| 기존 1 / 4·5 / 2번 동시성 | 47 / 72 / 115개 통과 |
| 기존 3번 `CommunityPostPrivacyIntegrationTest` | 24개 통과 |
| 신규 `CommunityPostAnonymityPolicyIntegrationTest` | 23개 통과 |
| `frontend/npm test -- --watchAll=false --runInBand` | 19개 suite, 190개 통과: 기존 185개 + 신규 5개 |
| `frontend/npm run build` | 성공 |
| `node backend/tools/sync-contract-docs.cjs --check` | 70 endpoints / 71 record DTOs / 29 entities 통과 |
| `git diff --check` | 통과 |

최종 실패·오류·건너뜀은 모두 0개다.

신규 백엔드 23개는 일반/익명 생성(2), 동일 값·생략·명시적 null의 실제 JSON 수정(6),
반대 값 요청의 제목·본문·이미지·기존 파일 보존(2), 정상 이미지 교체(2), 비작성자 거절(2),
삭제 글 거절(2), 실제 H2 잠금 대기 후 거절(2), 사용자 PUT·관리자 PATCH 우회 거절(2),
잘못된 JSON 타입(2), 과거 전환 데이터의 기존 공개 방 보존(1)을 검증한다.
파일 검증은 JUnit 임시 디렉터리의 실제 파일과 DB 이미지 연결을 비교한다.
반대 값은 파일 저장 전에 거부하며, 기존 파일 저장 후 실패 시 정리는 4·5번 전체 회귀에 포함한다.

신규 프론트 5개는 일반/익명 수정 재진입(2), 저장 실패 후 초안·고정 익명 상태·재시도(2),
생성 시 양방향 체크박스 선택(1)이다. 기존 수정 2개도 일반/익명 읽기 전용 상태와
anonymous 없는 payload를 검증하도록 갱신했으며 이미지 수정 테스트의 기대 payload도 갱신했다.

초기 전체 백엔드 실행에서 기존 사후 롤백 테스트 한 곳이 금지된 전환을 먼저 요청하여
예상 예외가 달라졌다. 동일 값을 보내 실제 본문·이미지 변경 후 강제 롤백까지 검증하도록
수정했고 최종 전체 975개를 다시 실행했다. 초기 프론트 선택 실행의 작성 테스트 1개는
시간초과 후 동일 코드 재실행에서 통과했으며 전체 190개 실행에서도 재현되지 않았다.

Entity·Service·프론트의 익명 변경 경로를 제거했으며 일반/익명 생성, 제목·본문·이미지 수정,
작성자 ID 마스킹, mine, 채팅 연결과 기존 계정 → 게시글 → 댓글 잠금은 유지한다.
기존 데이터·방의 일괄 수정과 스키마 마이그레이션은 없다. 실제 MySQL·다중 JVM·실브라우저·
STOMP 네트워크 전송은 미검증이다. 브랜치 변경·커밋·푸시·PR 생성은 수행하지 않았다.

## 검증 범위

### 초기 ID 마스킹 작업의 변경 파일

저장소 루트 기준이며 초기 ID 마스킹 작업 당시 신규 파일은 두 개다.

| 파일 | 변경 |
| --- | --- |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityAuthorResponse.java` | 익명 게시글 작성자 ID 마스킹 |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityPostResponse.java` | 서버 조회자 기준 mine |
| `backend/src/main/java/com/kkirikkiri/community/post/dto/CommunityPostDetailResponse.java` | 상세 mine |
| `backend/src/main/java/com/kkirikkiri/community/post/service/CommunityPostService.java` | 목록·상세에 인증 조회자 전달 |
| `backend/src/main/java/com/kkirikkiri/chat/dto/ChatRoomCreateRequest.java` | 출처별 nullable targetUserId 계약 설명 |
| `backend/src/main/java/com/kkirikkiri/chat/service/ChatService.java` | 게시글 출처의 실제 작성자 조회 |
| `backend/src/test/java/com/kkirikkiri/community/CommunityPostPrivacyIntegrationTest.java` | 신규: 실제 API·서비스·JSON 통합 검증 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/CommunityPostServiceTest.java` | 변경된 ID 마스킹 계약 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/PostLikeListIntegrationTest.java` | 익명 ID·mine 기대값, 기존 정렬·집계 검증 유지 |
| `backend/src/test/java/com/kkirikkiri/community/post/service/PostNullableDormitoryIntegrationTest.java` | nullable 생활관과 익명 ID·mine 검증 |
| `backend/src/test/java/com/kkirikkiri/chat/service/ChatServiceTest.java` | 출처 검증보다 앞선 대상 조회 mock 제거 |
| `frontend/src/pages/community/CommunityPage.js` | mine 기반 내 글·관리·수정 경로 |
| `frontend/src/pages/community/CommunityPostPage.js` | mine 기반 관리 버튼, 익명 출처 채팅 |
| `frontend/src/pages/chat/useChat.js` | Router state에서도 작성자 ID 없는 POST 채팅 |
| `frontend/src/pages/community/CommunityPage.test.js` | nullable ID·채팅·권한·재마운트 회귀 |
| `frontend/src/pages/community/communityData.test.js` | null ID와 mine 보존 검증 |
| `frontend/src/pages/chat/useChat.test.js` | 작성자 ID 없는 방 생성·익명 표시 검증 |
| `backend/API.md` | 공개 응답·채팅 계약 및 DTO 스냅샷 |
| `backend/ERD.md` | 저장 author_id와 공개 ID 구분, 스키마 유지 |
| `backend/DEPLOYMENT.md` | 프론트·백엔드 동시 적용과 정책 보류 |
| `backend/COMMUNITY_POST_PRIVACY.md` | 신규: 원인·파일·검증·남은 위험 안내 |

`CommunityPostPrivacyIntegrationTest`는 test 프로필/H2에서 실제 SecurityFilterChain,
Controller·서비스·JPA·Jackson 3 JSON을 검증한다. 인증 테스트 Principal을 주입하며
실제 로그인/SMTP나 네트워크 WebSocket 연결은 사용하지 않는다.

- 목록·검색의 LATEST/VIEWS/LIKES, 작성자/타인/게스트, 상세의 익명/일반 정보와 mine.
- 내 활동의 ID 마스킹과 인증 필수, 작성·수정·삭제, 위조 mine/userId의 권한 미획득.
- 작성자 ID 생략 및 위조 ID의 대상 변경 불가·추측 판별 불가, 재진입·목록 재조회 복원.
- 일반/익명/ROOMMATE 방 분리, REST 메시지/실제 broadcast DTO JSON의 senderId 마스킹,
  작성자 본인의 ME 방향과 상대 참여자의 공개 프로필 유지, 제3자 메시지 접근 거절.
- 삭제 출처의 새 채팅 거절과 기존 방 유지, 익명 댓글·답글 ID/번호/소유 여부,
  삭제 후 번호 유지, 관리자 전용 신원과 일반 API 분리.

기존 채팅 단위 테스트는 다른 익명 게시글 분리, 일반 출처 갱신, UNIQUE 충돌 복구,
나간 방 거절, 출처 변경·삭제 후 익명 방 유지도 검증한다.
기존 동시성 테스트 47/72/115개는 모두 전체 테스트에 포함한다. 추가 정책 적용 시
1번의 사후 롤백 검증 한 곳은 동일 익명 값을 보내도록 조정하여 실제 본문·이미지 변경 후
롤백을 계속 검증한다. 잠금 경합·계정 상태·부모 삭제·이미지 제한 검증은 유지한다.
프론트는 nullable ID 표시, mine 기반 관리·편집, 재마운트 복원, ID 없는 채팅 시작,
익명 프로필·REST/WebSocket 메시지 방향의 기존 검증을 실행한다.

### 초기 ID 마스킹 작업 실행 결과 (2026-10-19)

| 검증 | 결과 |
| --- | --- |
| `backend/gradlew.bat test bootJar` | 성공, 952개 통과: 기존 928개 + 신규 24개 |
| 기존 1번 `CommunityContentConcurrencyIntegrationTest` | 47개 통과 |
| 기존 4·5번 `CommunityCreationAndImageConcurrencyIntegrationTest` | 72개 통과 |
| 기존 2번 `CommunityAccountConcurrencyIntegrationTest` | 115개 통과 |
| 신규 `CommunityPostPrivacyIntegrationTest` | 24개 통과, 실제 API JSON·broadcast DTO JSON 포함 |
| `frontend/npm test -- --watchAll=false --runInBand` | 19개 suite, 185개 통과 |
| 신규 프론트 회귀 | 7개 통과: CommunityPage 6개, useChat 1개 |
| `frontend/npm run build` | 성공 |
| `node backend/tools/sync-contract-docs.cjs --check` | 70 endpoints / 71 record DTOs / 29 entities 통과 |
| `git diff --check` | 통과 |

최종 실행의 실패·오류·건너뜀은 모두 0개다. 신규 프론트 검증은 익명 글 채팅 시작,
목록·내 글·상세의 재마운트 복원(3개), mine=false일 때 관리 버튼과 수정 경로 차단,
Router state에서 작성자 ID 없는 채팅 시작이다. 기존 데이터 정규화 테스트도 null ID/mine
계약으로 갱신했다. 초기 실행에서 발견된 과거 실제 ID 기대값과 선행 대상 조회 mock은
새 계약에 맞게 수정했으며 최종 전체 실행에서 다시 통과했다.

테스트와 빌드 외 운영 DB·공유 DB·SMTP·MySQL 다중 JVM·실브라우저·실제 STOMP 네트워크
전송은 미검증이다. 브랜치 전환·커밋·푸시·PR 생성은 수행하지 않았다.

## 남은 위험 및 주의사항

- 과거에 노출된 ID, 브라우저·외부 캐시·저장 응답을 회수할 수 없다.
- 정책 적용 전 일반 ↔ 익명 전환이 있었던 게시글은 기존 공개 방의 `sourcePost`와
  공개 프로필을 연결할 수 있다. 확정 정책에 따라 과거 게시글·방은 소급 수정하지 않는다.
  새 변경 요청은 양방향 거부하므로 배포 후 정상 수정 API로 이 전환을 만들 수 없다.
  기존 방 재사용·출처·생성 당시 익명성 유지 정책은 그대로 유지한다.
- 동일인이 일반 댓글에 신원을 공개하면 연결 가능하다.
  본문·이미지·시간·자발적 채팅 내용의 추론이나 여러 계정의 자기 글 채팅 거절을 이용한
  식별까지 막는 강한 익명성은 별도의 정책·위협 모델이 필요하다.
- 실제 MySQL/InnoDB 잠금·다중 JVM, 실브라우저, 운영 STOMP 전송과
  기존 DB 익명 키 적용 상태는 이 H2/MockMvc/Jest 검증의 대상이 아니다.

따라서 현재 익명 출처의 ID 노출 수정과 과거 공개 신원의 소급 익명화는 구분해 보고한다.
