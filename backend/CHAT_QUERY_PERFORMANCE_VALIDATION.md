# 채팅 조회 제한·목록 성능 검증

검증일: 2026-10-19 (Asia/Seoul). 브랜치 `test/chat-validation`.
범위는 사용자 요청의 문제 4·5이며, 앞선 문제 1·3의 미커밋 변경을 보존했다.
운영/공유 DB 접속·변경, Docker 변경, 마이그레이션 실행, Git commit/push/merge는 하지 않았다.

## 1. 작업 결과

완료(문제 4·5 코드 수정·H2 자동 검증 기준). 메시지 조회 상한 및 DB 기반 채팅방 목록 페이지 조회를 구현했다.
전체 백엔드 1,208개, 프론트 202개와 양쪽 빌드가 통과했다. 운영 배포 성공을 검증한 것은 아니다.

## 2. 문제 원인

- `ChatMessageListRequest`에 최대 size 제약이 없었고 `getMessages`도 최소값과 offset만 검사했다.
  큰 size가 DB 조회·응답 직렬화로 전달될 수 있었다.
- `getRooms`가 사용자 참여 방을 전부 조회하고 각 방에 `toRoomResponse`를 호출했다.
  상대 참여자/프로필/설문/매칭 점수/마지막 메시지/미읽음 수를 방별로 조회한 뒤 Java에서 검색·정렬했다.
  실제 기존 경로를 실행해 방 수 증가에 따른 SQL 증가를 확인했다.

## 3. 생성·수정 파일

아래는 이번 문제 4·5의 파일이다. 이전 문제 1·3 변경 전체와 혼동하지 않는다.
`backend/src/main/java/com/kkirikkiri/` 이하를 M, `backend/src/test/java/com/kkirikkiri/` 이하를 T로 표기한다.

| 파일 | 이번 변경 |
|---|---|
| M/chat/dto/ChatMessageListRequest.java | 기본 50 / 최대 100, Bean Validation 및 offset 검사 |
| M/chat/dto/ChatRoomListRequest.java | page/size 추가, 기본 50 / 최대 100, offset 검사 |
| M/chat/dto/ChatRoomPageResponse.java (생성) | content 및 페이지 메타데이터 |
| M/chat/dto/ChatRoomCountsResponse.java (생성) | 전체 카테고리/고정 개수 |
| M/chat/controller/ChatController.java | 목록 페이지 DTO, 선택 방 단건 GET |
| M/chat/service/ChatService.java | 서비스 상한 검사, 페이지별 batch 조합, 기존 단건 변환 재사용 |
| M/chat/repository/ChatRoomRepository.java | DB 검색/페이지/정렬, 최근 메시지 scalar projection, 전역 개수 집계 |
| M/chat/repository/ChatMessageRepository.java | 현재 페이지 방 ID들의 is_read=false 집계 |
| M/chat/repository/ChatRoomListRow.java (생성) | 내부 목록 scalar projection |
| M/chat/repository/ChatRoomCategoryCount.java (생성) | 내부 카테고리 집계 projection |
| M/chat/repository/ChatUnreadCount.java (생성) | 내부 방별 미읽음 집계 projection |
| T/chat/service/ChatServiceTest.java | 기존 검증을 새 batch 경로에 맞춤, 서비스 상한/overflow 추가 |
| T/chat/service/ChatRoomReadPerformanceIntegrationTest.java (생성) | 실제 JPA SQL 수/DB 제한, 목록 동작, 메시지 128건 페이지 검증 |
| T/chat/controller/ChatQueryControllerTest.java (생성) | JWT Principal, 잘못된 숫자·상한·overflow, 페이지 응답 |
| T/community/CommunityPostPrivacyIntegrationTest.java | 목록 content 경로만 조정, 기존 익명성 assertion 유지 |
| T/community/CommunityPostAnonymityPolicyIntegrationTest.java | 목록 content 경로만 조정, 기존 정책 assertion 유지 |
| frontend/src/api/chatApi.js | 목록 page/size와 단건 GET |
| frontend/src/api/chatApi.test.js | 요청 필터/페이지, 단건 요청 계약 검증 |
| frontend/src/pages/chat/useChatRooms.js (생성) | 순서 ID와 방 캐시 분리, 더 보기/재시도, 검색·필터 초기화, 늦은 응답 무시 |
| frontend/src/pages/chat/useChatRooms.test.js (생성) | 중복 ID, 다음 페이지 경합/실패, 필터 변경, 페이지 밖 선택 검증 |
| frontend/src/pages/chat/useChat.js | 생성·수신·설정·나가기 후 목록 갱신, 읽음 후 단건 미읽음 조회 |
| frontend/src/pages/chat/useChat.test.js | 새 페이지 계약에 맞춘 fixture, 기존 읽음/출처/실패 회귀 유지 |
| frontend/src/pages/chat/ChatPage.js | 서버 순서/개수 사용, 더 보기와 재시도 표시 |
| frontend/src/pages/chat/ChatPage.test.js | 서버 필터/카테고리 개수/더 보기와 기존 UI 회귀 |
| backend/API.md | query 상한, 페이지 객체·counts, 단건 GET, DTO/Controller 스냅샷 |
| backend/ERD.md | 컬럼/제약 변경 없음 및 인덱스 후보 명시 |
| backend/DEPLOYMENT.md | 클라이언트 동시 전환, 검증 범위와 MySQL 확인 항목 |
| backend/migrations/README.md | 조회 인덱스 수동 절차 연결 |
| backend/migrations/20261019_chat_query_indexes.sql (생성) | 비고유 인덱스 후보 3개, 미실행 |
| backend/migrations/20261019_chat_query_indexes.md (생성) | 기존 인덱스/실행계획/DDL 영향/롤백 검토 절차 |
| backend/CHAT_QUERY_PERFORMANCE_VALIDATION.md (생성) | 이번 보고서 |

다른 도메인의 제품 코드, CSS, application.properties, 참여자 쓰기 잠금·정확한 ID 읽음 처리,
ChatMessage Entity의 기존 is_read 구현은 이번 조회 작업에서 바꾸지 않았다.
기존 UserProfile/ProfileSurvey/RoommateMatch batch repository 메서드를 재사용했다.

## 4. 해결 방법

- 두 목록 모두 기본 size=50, 최대 100이다. 기존 프론트 요청을 보존하며 기본값의 두 배로
  한 응답의 조회·직렬화량을 제한한다. 메시지 본문은 기존 최대 2,000 코드 포인트다.
  Controller Bean Validation과 Service 검증을 모두 적용하며 int 범위를 넘는 offset도 거절한다.
  정상 메시지 페이지 객체와 sentAt DESC/messageId DESC는 유지한다.
- 방 목록은 0부터 시작하는 offset 페이지다. content에 기존 ChatRoomResponse를 유지하고
  totalElements/totalPages/first/last 및 전체 category/pinned counts를 추가한다.
  조용히 첫 50개만 남기는 상한 방식은 제외했다. 프론트는 다음 페이지 요청·실패 재시도를 제공한다.
  배열 전수 조회를 유지하면 이번 전체 조회 문제를 해결하지 못하므로 서버/프론트 계약을 함께 전환했다.
- DB에서 나간 방/비활성 방 제외, category, 표시 닉네임 검색을 적용한다.
  q는 trim/소문자 부분 검색, %, _, !는 escape하여 기존 문자 검색 의미를 유지한다.
  익명 상대의 실제 닉네임으로 검색되지 않게 마스킹을 먼저 적용한다.
- 고정 DESC → 최근 sentAt DESC(NULL 마지막) → roomId DESC를 DB에서 정렬한다.
  최근 메시지 동률은 messageId로 결정한다. 최근 메시지 anti-NOT EXISTS와 참여자 수 검사는
  목록 SQL 내부에 포함되며 방별 JDBC 왕복을 만들지 않는다. Room/Member Entity 전체를 로딩하지 않는다.
- 현재 페이지의 공개 상대 ID만 모아 프로필/설문/점수를 각 한 번 조회하고, 방 ID별 미읽음을 한 번 집계한다.
  익명 작성자의 id/사진/전공/기숙사/소개/점수는 null, nickname=익명, tags=[]다.
  기존 정확한 ID receipt의 is_read=false를 그대로 사용한다. 시간·최대 ID 기반 읽음으로 되돌리지 않았다.
- 선택한 방은 목록 필터/페이지와 별도 캐시로 유지하고 필요한 경우 단건 조회한다.
  생성/수신/고정/나가기 후에는 첫 페이지로 갱신한다. 검색·필터 변경 시 이전 요청 세대를 무효화하고
  다음 페이지는 ID를 중복 제거하여 추가한다. stale 목록이 새 출처·설정을 덮어쓰는 경합을 보호한다.

고정 데이터의 페이지 순서는 결정적이다. 다른 요청 사이에 정렬값이 바뀌는 offset 페이지에는
동일 snapshot 보장이 없고 다른 클라이언트의 동시 변경에 따른 이동/누락 가능성도 남는다.
cursor/snapshot 전환은 이번 요구 범위에 포함하지 않았다.

## 5. 성능 검증

SpringBootTest + test 프로필의 H2 메모리 DB, Hibernate Statistics의 prepareStatementCount를 사용했다.
fixture 생성은 제외하고 조회 전 통계를 초기화했다. 각 서비스 호출은 새 persistence context를 사용한다.
기존 main class를 재컴파일하기 전에 동일 fixture로 기존 getRooms를 실행한 결과와 수정 후를 비교했다.
사용자별 상대 프로필/매칭 점수 및 방별 메시지 3건(미읽음 상대/자기/읽은 상대 각 1건)을 준비했다.

| 방 수 | 수정 전 반환/SQL | 수정 후 첫 페이지 반환/SQL |
|---:|---:|---:|
| 1 | 1 / 10 | 1 / 6 |
| 10 | 10 / 73 | 10 / 6 |
| 60 | 60 / 423 | 50 / 7 |

수정 후는 방 페이지 1회, 필요 시 총수 count 1회, 프로필/설문/점수/미읽음 각 1회,
전체 category/pinned 집계 1회로 일반 공개 상대 페이지의 최대 7회다.
Spring Data가 마지막 페이지의 총수를 추론하면 count SQL을 생략하여 6회다.
익명만 있는 페이지나 빈 페이지는 불필요한 batch 조회를 생략한다.
테스트는 1/10/60 규모에서 SQL<=7, SQL의 ORDER BY와 DB 페이지 제한,
ChatRoom/ChatRoomMember Entity load=0을 실제로 확인했다.

원본 측정 CSV는 로컬 `backend/build/chat-read-performance/before.csv`, `after.csv`에 남겼다(빌드 산출물).
before의 1방 행은 초기 fixture 보완 전 부분 실행과 전체 성공 실행 때문에 두 번 기록됐다.
after는 재실행 때마다 추가된다. 위 표는 성공 측정값이다.
추가 방의 다음 페이지는 별도 요청 비용이 발생한다. 전수 60개를 가져온 응답과 첫 50개 응답의
지연시간을 동등 비교한 결과가 아니며 퍼센트 속도 향상 수치는 산출하지 않았다.

미측정: MySQL 실행계획/실제 읽은 행 수, 대량 메시지 anti-NOT EXISTS 비용, p95/p99 지연,
응답 메모리·직렬화 시간, 대규모 offset, 운영 Collation 및 동시·다중 JVM 부하.
쿼리 왕복 수가 일정하다는 사실이 DB 내부 연산량 일정 또는 운영 성능 보장을 뜻하지 않는다.

## 6. 테스트 결과

테스트를 삭제/비활성화하거나 기존 권한 검증을 완화하지 않았다.

- 백엔드 최종 전체 실행: `gradlew.bat test bootJar --console=plain` 성공
  (2026-10-19 22:43 KST, 6m 9s). XML 합계 1,208개, 실패/오류/skip 0.
  chat 패키지 198개, ChatStompInterceptor 36개, 연결된 커뮤니티 익명성/정책 47개가 포함된다.
  ChatServiceTest 116개, ChatQueryControllerTest 13개, ChatRoomReadPerformanceIntegrationTest 7개이며,
  기존 ChatReadConcurrencyIntegrationTest 12개, SharedAccountConcurrencyIntegrationTest 5개,
  ChatReadControllerTest 10개가 모두 통과했다. test와 bootJar를 실제 실행한 결과다.
- 전체 첫 실행의 실패 1개는 커뮤니티 정책 테스트가 목록 응답을 배열로 읽던 부분이다.
  `data.content` 경로만 조정한 뒤 위 전체 실행으로 다시 확인했다. 익명성 assertion은 유지했다.
- 실행 로그의 stderr 리디렉션 때문에 PowerShell 도구가 종료 1로 표시했지만 Gradle은
  BUILD SUCCESSFUL/cmd exit 0을 기록했고 XML에도 실패가 없다. 리디렉션 없는 재확인은 종료 0이었다.
  그 재확인은 태스크 UP-TO-DATE이며 새로운 테스트 실행으로 집계하지 않았다.

- 프론트 최종 전체 실행: `npm test -- --watchAll=false --runInBand --json --outputFile=../backend/build/chat-frontend-final.json`
  성공. 21 suites / 202개 통과, 실패/skip 0. 이 중 ChatPage 35, useChat 14, useChatRooms 5, chatApi 3으로
  채팅 관련 57개가 포함된다. 별도 채팅 4 suites / 57개 실행도 통과했다.
- 프론트 `npm run build` 성공(Compiled successfully). React hook ESLint 경고는 해결했다.
  도구의 Node fs.F_OK deprecation 경고는 남지만 빌드 실패는 없다.
- 문서 `node backend/tools/sync-contract-docs.cjs --check` 성공:
  72 endpoints / 74 record DTOs / 30 entities. `git diff --check`도 성공했다.

- Controller/Service: 0/음수/100/101/Integer.MAX_VALUE/정수 바인딩 범위 초과,
  음수 page/offset overflow, 기본 50, 빈 페이지, 기존 envelope와 Principal 검증.
- 실제 JPA: 128개 메시지에서 100+28 페이지, 기본 50, 동률 ID 내림차순,
  페이지 전체 중복/누락 없음, 빈 페이지 및 타인 조회 차단.
- 실제 JPA 방 목록: 빈 사용자/빈 페이지, 1/10/60개, category/일반·익명 검색,
  %, _, ! escape, 고정/최근/동률/NULL 정렬, 새 메시지·나가기·고정 해제 반영,
  프로필/완료 설문 태그/점수, 익명 게시글·댓글 보호, 마지막 메시지, 정확한 미읽음 수.
- 기존 문제 1·3: USER→membership 잠금/refresh, 나가기 덮어쓰기 방지,
  읽음 시각 단조 증가, 지연 커밋·동일 시각·정확한 ID 확인·rollback 회귀를 전체 테스트에서 재실행했다.
- 프론트: 더 보기와 global counts, 중복 ID 제거, 중복 클릭 단일 요청,
  다음 페이지 실패/재시도, 검색·필터 후 stale 페이지 무시, 페이지 밖 직접 선택,
  stale 단건 응답 무시, 기존 읽음·출처·설정·프로필 UI 회귀를 실행했다.

## 7. 기존 기능 영향

- API: GET /api/chat/rooms의 data 배열 → 페이지 객체는 계약 변경이다. 각 방 item 필드는 유지한다.
  page/size와 counts 및 GET /api/chat/rooms/{roomId}가 추가된다. 메시지 API는 size 상한만 강화한다.
  기존 읽음 요청 messageIds 본문·응답과 잠금 순서는 유지한다.
- 프론트: 서버 페이지·검색·필터·순서·전체 개수 사용으로 전환했다. 스타일 파일은 바꾸지 않았다.
  목록 밖 선택 방도 유지하며 일부 방이 표시 없이 잘려 사라지는 방식은 사용하지 않았다.
- DB: 이번 작업의 컬럼/제약 변경은 없다. 앞선 is_read 마이그레이션은 여전히 필요하다.
  비고유 조회 인덱스 후보 3개만 수동 SQL/절차로 제공했으며 실제 생성하지 않았다.
- 같은 상대 ROOMMATE/COMMUNITY 분리, 익명성, 프로필/출처 표시,
  고정 2개 제한, 나간 방 제외, 마지막 메시지·미읽음 집계를 유지한다.

## 8. 남은 문제와 검증

기존 점검의 문제 2·6·7·8·9는 이번 수정 범위 밖이다. 기존 WebSocket 연결 후 권한 재검증,
Simple Broker의 인스턴스 간 전달 등 알려진 배포 보류 사항은 이 성능 작업으로 해결되지 않는다.
실제 MySQL·두 JVM은 사용자 요청에 따라 최종 통합 단계로 남긴다.

[인덱스 절차](migrations/20261019_chat_query_indexes.md)의 EXPLAIN/DDL/Collation/대량 메시지/
쓰기 비용과 [이전 읽음 전환](migrations/20261019_chat_message_receipts.md)의 InnoDB RR,
잠금·rollback·지연 커밋·수동 데이터 전환·복구·두 JVM 검증이 필요하다.
앞쪽 % 닉네임 검색과 correlated 최신 메시지/총수 집계 비용은 대표 MySQL 데이터로 확인해야 한다.
구버전 배열 클라이언트를 확인하고 관련 서버/프론트를 함께 전환한다.

## 9. 최종 판정

문제 4·5의 수정 및 요청한 자동 테스트/빌드/SQL 수 검증은 완료했다. 현재 자동 검증에 추가 실패는 없다.
문제 1·3의 동시성·정확한 ID 읽음 회귀도 통과했다.
운영 배포 판정은 **배포 보류**다. 이번 범위 밖의 기존 보류 사유와 MySQL·다중 JVM,
앞선 읽음 전환 및 신규 조회 인덱스의 실제 검증이 남는다. 관련 클라이언트와 페이지 계약도 함께 전환해야 한다.
이번 검증만으로 실제 운영 배포 성공이나 MySQL 성능·다중 JVM 정합성을 확정하지 않는다.
