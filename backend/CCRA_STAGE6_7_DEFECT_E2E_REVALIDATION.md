# 6·7단계 결함 수정 후 React·Spring Boot·MySQL E2E 재검증

2026-10-23, Asia/Seoul. 기준:
[기존 P0 결과](CCRA_STAGE6_7_P0_E2E_VALIDATION.md),
[수정·회귀 결과](CCRA_STAGE6_7_DEFECT_FIX_VALIDATION.md).
이번 실제 실행의 테스트별 결과·HTTP/STOMP/JDBC 근거·원본 파일 지문은
[재검증 JSON](validation/ccra-stage6-7-revalidation-20261023.json)에 보관했다.

**요청 범위 9개 사례 모두 PASS. 두 결함 모두 수정 후 E2E PASS이며, 추가 운영 코드 수정은 없었다.**
이번 결과는 두 결함과 지정 Community·Chat 회귀에 한정한다. P0 8개 전체나 P1을 다시 실행한 결과는 아니다.
과거 P0의 6 PASS / 2 FAIL 원본은 변경하지 않았다.

## 1. 전용 자원 확인과 마이그레이션

| 식별 항목 | 실제 확인값 |
| --- | --- |
| 컨테이너 / 소유 라벨 | `kkiri-e2e-82cdabce6030` / `kkiri.e2e.owner=82cdabce6030` |
| 상태 / 이미지 | running / `mysql:8.0.46-oraclelinux9` |
| 바인딩 | `127.0.0.1:13316` → MySQL 3306 |
| 스키마 / 접속 계정 | `kkiri_e2e_82cdabce6030` / `e2e_82cdabce6030@%` |
| 서버 UUID | `f8a3a22f-ce99-11f1-bd9a-621e38ee5f3d` |
| JDBC 서버 / 세션 | MySQL 8.0.46 / hostname `kkiri-e2e` / UTC `+00:00` |

이전 run.json과 실제 Docker 이름·소유 라벨·바인딩·이미지가 일치함을 확인했다.
JDBC에서는 기존 guardedConnection을 재사용해 DB명·계정·UUID·버전·호스트·UTC 및
해당 스키마에만 한정된 권한을 재확인했다. 다른 Docker 자원을 열거하거나 다른 DB에 연결하지 않았다.
실행 전에 React 13000, Spring 18080의 포트가 비어 있음을 확인했으며 공유 실행기를 수정하지 않았다.

`20261023_chat_send_idempotency.sql`은 nullable 컬럼과 UNIQUE 추가만 포함하며 데이터 삭제/UPDATE/초기화가 없다.
실제 물리 테이블은 Linux MySQL의 소문자 `chat_message`여서 이 식별자만 맞춰 다음 DDL을 수동 실행했다.
실행한 문장 전체는 `3bee6cb70e3b/migrate.json`에도 기록했다.

```sql
ALTER TABLE `chat_message`
    ADD COLUMN client_message_id VARCHAR(36) NULL,
    ADD CONSTRAINT uk_chat_message_client UNIQUE (chat_room_id, sender_id, client_message_id);
```

**마이그레이션 PASS.** information_schema에서 VARCHAR(36)/nullable=YES,
NON_UNIQUE=0과 3개 키의 순서 `chat_room_id → sender_id → client_message_id`를 확인했다.
변경 전 31개 테이블, 기존 147행의 PK·전체 기존 컬럼을 해시로 기록했다. 직후 모든 행의
누락·기존 컬럼 변경이 0이었다. 기존 메시지 21행은 모두 새 컬럼 NULL로 유지됐다.
Hibernate `validate`와 새 Entity/DTO를 통한 기존 NULL 메시지 21행 읽기도 통과했다.
후속 실행에서는 DDL을 반복하지 않았다.

## 2. 두 결함과 기존 기능 결과

| 사례 | 결과 | 실제 실행 근거 |
| --- | --- | --- |
| RV-01 일반 댓글 POST 성공 → GET 실패 | PASS | postId=17, commentId=17. 실제 POST 200 후 GET만 503 주입. 초안 비움·조회 실패 안내. GET 재조회 200 후 DB 동일 1행·POST 총 1회 |
| RV-01 익명 댓글의 같은 상황 | PASS | postId=18, commentId=18. POST 1회/DB 1행, 익명 author.id=null·mine=true 유지 |
| RV-01 일반 답글의 같은 상황 | PASS | postId=19, parentId=19, replyId=20. 작성기 닫힘·저장 안내, 재조회 후 동일 답글 1행 |
| RV-01 익명 답글의 같은 상황 | PASS | postId=20, parentId=21, replyId=22. POST 1회/DB 1행, 익명 마스킹 및 부모 관계 유지 |
| RV-02 댓글·답글 CRUD·익명 처리 | PASS | postId=21, commentId=23~26. 일반/익명 댓글·답글 각각 생성·조회·수정·삭제 API 200. 타인 수정 403. 신규 4행만 DELETED로 소프트 삭제, 행 자체는 보존·상세 댓글 목록에서는 제외 |
| RV-03 채팅 SEND 전송 실패 → 초안 보존 | PASS | roomId=8. 전송 ID `17f5b0cb-17f1-4e62-9b07-390e329f0974`의 첫 SEND를 브라우저→서버 경로에서 차단. DB 0행·입력/대기 내용 유지·읽기 전용 확인. 재시도는 같은 ID/본문, 저장 messageId=22 1행 확인 후 입력 비움·상대 수신 |
| RV-04 저장 후 확인 유실·새로고침·재시도 | PASS | roomId=9. ID `c18a9683-d5d6-40fa-b42a-0b81686c9089`의 서버 저장 messageId=23 확인. 송신자의 STOMP echo와 메시지 REST 확인만 차단해 입력 보존. 새로고침 후 대기 복원·같은 ID 재전송, 기존 messageId=23으로 확인·DB 1행 유지 |
| RV-05 실제 MySQL 동시성·UNIQUE·구 계약 | PASS | roomId=10. 독립 STOMP 연결 8개에서 같은 ID SEND를 1ms 안에 큐잉. 8개 응답 모두 messageId=25·DB 1행. 같은 ID로 8회 더 요청해도 같은 결과. 물리 중복 INSERT는 SQLSTATE 23000/오류 1062, 시도 트랜잭션은 rollback. 다른 ID/같은 본문 2건은 서로 다른 메시지, ID 없는 기존 형식 2건도 정상 저장 |
| RV-06 양방향 STOMP·재연결·REST 복구 | PASS | 최종 roomId=12. REST 메시지 조회를 막은 상태에서 A→B messageId=39, B→A=40의 실제 STOMP 전달·입력 비움 확인. 실제 브라우저 소켓 종료 후 새 연결 2번째 생성·누락 이력=41 복구·재연결 송신=42. 이후 B의 소켓을 종료하고 재연결/수신을 막은 상태에서 REST 200으로 messageId=43이 UI에 복구. 해당 메시지의 native STOMP 수신 0건·DB 1행 |

댓글·답글 GET 실패는 브라우저 주입 오류이며 실제 서버 503 장애라고 집계하지 않았다.
네 가지 조합 모두 POST 성공 후 작성 실패 alert가 발생하지 않았으며 재시도 버튼은 목록 조회만 수행했다.
채팅에서는 publish 호출, 실제 STOMP 전달, REST 복구, DB 저장/중복 여부를 별도로 대조했다.

동시성은 8개의 실제 인증 WebSocket/STOMP 세션과 실제 MySQL을 사용했다.
첫 전송과 재시도 각각 8개의 저장 응답이 동일 messageId로 돌아왔고 DB 결과도 한 행이었다.
DB 잠금 대기 시간이나 스레드별 락 획득 순서를 계측한 것은 아니다.
직접 UNIQUE 검증은 이 실행의 새 송신자·새 메시지만 대상으로 했으며 중복 INSERT 성공 여부와
오류 코드 확인 후 rollback했다. 기존 행을 삭제하거나 수정하지 않았다.

## 3. 실행 근거와 검증 도구 문제

| 실행 ID | 범위 | 원본 결과 |
| --- | --- | --- |
| `3bee6cb70e3b` | 마이그레이션 + 9개 사례 | 8 PASS / 1 FAIL |
| `62e3c9338423` | 마이그레이션 재적용 없이 RV-06만 후속 검증 | 1 PASS |

최초 RV-06 FAIL은 polling 전용 전달을 증명하려던 검증 도구의 실패다.
Playwright route의 소켓 close만으로 실제 앱 WebSocket의 종료가 보장되지 않아 수신이 계속됐다.
이 상태를 PASS 처리하지 않고 원본 FAIL을 보존했다.
도구에서 현재 문서의 native WebSocket을 추적·종료하고 readyState=CLOSED를 확인하며,
닫힌 연결의 전달을 차단하고 실제 native MESSAGE 이벤트도 별도로 검사하도록 보완했다.
운영 코드는 변경하지 않았고 이 사례만 재실행해 PASS를 확인했다.

따라서 고유 사례는 **9 PASS / 0 FAIL**, 실제 Playwright 시도는 **10회: 9 PASS / 1 도구 FAIL**이다.
추가 서비스 결함은 재현되지 않았다. 최초 실패와 모든 후속 테스트 행을 삭제하지 않았다.

- 관찰한 API HTTP 응답 531건: 실제 백엔드 524건(200=523, 403=1), 주입 503=7건.
  로그인 fixture·공통 화면의 배경 조회를 포함하며 HTTP 요청 종류의 개수나 전체 wire 트래픽 수가 아니다.
- 기존 JDBC snapshot 19회, 전송 ID 포함 별도 JDBC 작업 9회(메시지 snapshot 8회, UNIQUE probe 1회).
  이외에 마이그레이션 감사·행 지문 보존 검사·fixture 생성 작업이 별도로 있다.
- 브라우저 관찰 파일에는 HTTP 메서드/경로/상태, STOMP command 및 업무 메시지 ID/본문,
  오류 주입·대기 내용·DB 대조 결과를 남겼다. CONNECT 헤더·토큰·비밀번호는 기록하지 않았다.
  gateway 프레임 수에는 upstream 및 차단 관측도 있으므로 클라이언트 전달 건수와 혼동하지 않는다.
- 두 실행 모두 실행 중 소스 지문 불변, 업로드 지문 불변을 확인했다.
  이전 수정 보고서가 기록한 22개 파일의 SHA-256도 모두 일치했다.
- `prepareE2e`, JavaScript 문법 검사, 계약 스냅샷 73 endpoints / 75 record DTOs / 30 entities,
  `git diff --check` PASS. 이번에는 운영 코드 변경이 없어 기존 전체 Jest/인증/Calendar 테스트를 중복 실행하지 않았다.

원본은 `backend/build/full-service-e2e/<실행 ID>/`의 `run.json`, `results.json`, `browser-*.json`,
`database-*.jsonl`, `revalidation-database.jsonl`, `process-*.log`, `source-before/after.json`에 있다.
첫 실행의 `migrate.json`과 `original-row-fingerprints.json`, 각 실행의 `preservation.json`이
DDL 및 기존 행 보존 근거다. 요약 JSON에는 원본 파일 SHA-256도 포함했다.

실행한 명령:

```text
gradlew.bat prepareE2e
node backend/tools/ccra-stage6-7/revalidate.cjs
node backend/tools/ccra-stage6-7/revalidate.cjs --migration-from=3bee6cb70e3b --only-regression
node backend/tools/ccra-stage6-7/summarize-revalidation.cjs
```

이 명령들은 이번에 승인된 고정 자원용이며 일반 E2E 초기화/정리 명령이 아니다.
신규 실행은 사용자 승인과 대상 식별 확인 없이 재실행하지 않는다.

## 4. 변경 파일과 미해결 범위

이번 턴에 추가한 파일만 다음과 같다. 운영 코드·기존 마이그레이션 SQL·공유 실행기 4개 파일은 변경하지 않았다.

- `frontend/e2e/tests/ccra-revalidation.spec.cjs`: 요청된 9개 사례.
- `frontend/e2e/lib/ccra-revalidation.cjs`: 기존 P0/flow 재사용, 제한된 JDBC snapshot/UNIQUE IPC와 오류 주입·소켓 관측.
- `backend/tools/ccra-stage6-7/CcraRevalidationDatabase.java`: 지정 DB 식별·수동 DDL·행 지문·추가 fixture·UNIQUE 검증.
- `backend/tools/ccra-stage6-7/revalidate.cjs`: 지정 보존 자원 재사용과 자식 프로세스 수명 관리.
- `backend/tools/ccra-stage6-7/summarize-revalidation.cjs`: 명시한 두 실행의 결과·자원 상태 집계.
- 이 보고서와 `backend/validation/ccra-stage6-7-revalidation-20261023.json`.

요청 범위의 기능 FAIL은 남아 있지 않다. 현재 단일 Spring JVM·Simple Broker와 Chromium의
독립 BrowserContext 2개 기준이다. 여러 브라우저 엔진, WSS/프록시, 다중 JVM, 서버 프로세스 crash/restart,
장기 부하 검증은 하지 않았다. 기존에 설명한 sessionStorage의 탭 종료 이후 보존 한계도 변경하지 않았다.
운영/다른 팀 DB의 마이그레이션은 적용하거나 확인하지 않았다. P1은 실행하지 않았다.

## 5. 최종 자원·데이터 보존

- 컨테이너 `kkiri-e2e-82cdabce6030`은 최종 **running**, 같은 스키마·DB 계정·UUID다.
  Docker/스키마/MySQL 계정의 신규 생성·삭제는 모두 0회다.
- 보존 모드로 실행해 reset/clean/seed를 호출하지 않았다. 기존 행의 누락 0, 기존 업무 행 변경 0이다.
  실제 로그인 때문에 기존 `auth_limit_bucket` 3행의 제한 카운터/시각 등 운영 상태만 갱신됐다.
  이를 숨기거나 “모든 기존 행 불변”으로 보고하지 않는다. 인증 코드는 변경하지 않았고 수동 UPDATE/초기화도 하지 않았다.
- 각 실행에 필요한 응용 사용자 fixture 3명씩 총 6명만 추가했다. MySQL 계정을 추가한 것이 아니다.
- 주요 최종 행 수: 응용 사용자/프로필 각 18, 게시글 26, 댓글 26, 방 12, 참여 24, 메시지 42,
  이미지 연결 5, 신고 6. 검증 실패 시도와 신규 댓글 4개의 소프트 삭제 행도 그대로 보존한다.
- 기존 업로드 PNG 5개/340 bytes의 파일명·크기·SHA-256이 두 실행 전후 일치했다.
- Spring/React/브라우저 등 이번 실행의 자식 프로세스는 모두 종료했고 13000/18080 포트가 비어 있다.
  동시 Spring Boot JVM은 최대 1개였고 두 실행은 순차 진행했다. 컨테이너는 stop/restart/rm하지 않았다.
