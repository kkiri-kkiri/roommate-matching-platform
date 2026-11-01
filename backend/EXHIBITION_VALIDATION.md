# 전시회 시연 데이터 구현·검증 보고

2026-10-31 후속 실제 적용 결과는 [EXHIBITION_LOCAL_RUN.md](EXHIBITION_LOCAL_RUN.md)에 기록했다.
별도 로컬 MySQL DB에서 공식 apply, 반복 preview, 기존 DB 지문 보존, 실제 API 및 브라우저 검증을 완료했다.
이 문서의 2026-10-30 MySQL 미실행/1,502개 테스트 기록은 최초 구현 시점의 결과다.
후속 전체 H2 검증은 88개 클래스, 1,504개 테스트, 실패/오류/건너뜀 0이다.

작성일: 2026-10-30 (한국시간)

## 결과와 적용 범위

일반 서버 구동과 분리된 전시회용 생성·갱신 도구를 구현했다.
대상·건수 미리보기, 검토 토큰, 출처·소유권·수동 변경 확인, 반복 실행의 멱등성,
중단 시 재실행 차단, local 전용 보호와 DB 간 출처 오용 방지를 포함한다.

**실제 MySQL 연결·조회·적용, 기존 데이터 삭제·초기화, Docker 설정 변경을 수행하지 않았다.**
서버용 설정·스키마·Controller·DTO·Entity·프론트엔드는 변경하지 않았다.
테스트의 DB 쓰기는 Gradle이 강제한 test 프로필·H2 메모리 DB에 한정했다.

기존 DevDataInitializer와 SeedDataService의 자동 생성 방식은 분석 후 그대로 유지했다.
기존 CREATE가 갱신 기능이라고 가정하지 않으며 DELETE는 호출하지 않는다.
전시회 데이터는 기존 서비스로 저장하고, 매칭은 출처가 확인된 가상 일반 회원 사이에서 기존 계산기로 산출한다.

## 데이터 구성

모든 대상이 안전하게 생성/재사용 가능한 H2 시나리오에서 다음을 검증했다.
이는 현재 MySQL에 적용한 건수가 아니다. 실제 생성·갱신·보존·제외 건수는 추후 preview에서 확인해야 한다.

| 종류 | 구성 |
| --- | --- |
| 일반 회원 | 최대 30명. 기존 seed_user01~30의 ID·이메일·자격증명을 보존하고 프로필·설문 갱신 |
| 프로필 | 가상 닉네임 30개, 생활관 4개, 학부/전공 조합 6개, 학년·MBTI·자기소개 다양화 |
| 내 설문·선호 | 각 30개, 중요도 540개. 생활 리듬이 비슷하거나 다른 5개 그룹 |
| 게시글 | 게시판별 20개, 최대 80개. 기존 글은 ID와 작성 시각 유지 |
| 댓글·대댓글 | 160개. 익명 번호는 기존 서비스가 생성 |
| 좋아요 | 게시글 200개, 댓글 80개 |
| 채팅 | 8방, 참여자 16행, 메시지 33개. 룸메이트·일반 글·익명 댓글 출처, 읽음/미읽음 포함 |
| 개인 일정 | 개인 카테고리 3개·일정 12개. 실제 학교 수집 데이터와 분리 |
| 선택 관리자 시나리오 | `--reports`: 가상 관리자 최대 1명, 신고 대기·완료·반려 각 1개. 제재·삭제 조치 없음 |
| 매칭 | 일반 가상 회원 30명 기준 최대 870개 방향별 결과. 높은 점수와 60점 미만 결과 확인 |

기존 회원의 성별·출생연도·입학연도는 수정 서비스가 제공하지 않는 항목이므로 보존한다.
신규 일반 회원만 남녀 각 15명 구성이다. 기존 30명이 모두 남성이면 기존 성별을 SQL로 바꾸지 않는다.
신규 학년·입학연도·출생연도는 2026년 학적에 맞춰 구성했다.

## 변경 파일

| 파일 | 변경 목적 |
| --- | --- |
| `src/exhibition/java/exhibition/ExhibitionCatalog.java` | 가상 인물·설문·선호·글·댓글·대화·일정 카탈로그, 고정 시나리오 키 |
| `src/exhibition/java/exhibition/ExhibitionPlanner.java` | 읽기 전용 대상 계획·건수·SKIP 이유·검토 토큰 |
| `src/exhibition/java/exhibition/ExhibitionWriter.java` | 기존 서비스 호출과 생성/갱신 단계, 신규 시각 정합성, 시드끼리 매칭 |
| `src/exhibition/java/exhibition/ExhibitionDatabase.java` | 고정 SQL·바인딩 ID 기반 조회와 내용 지문 |
| `src/exhibition/java/exhibition/ExhibitionJournal.java` | DB·기준일에 귀속된 로컬 출처, 원자적 파일 교체·프로세스 잠금·APPLYING 차단 |
| `src/exhibition/java/exhibition/ExhibitionConfiguration.java` | 자동 초기화·스케줄·크롤러·매칭 비동기 작업 없는 최소 Spring 구성 |
| `src/exhibition/java/exhibition/ExhibitionTool.java` | preview/apply CLI, 명시적 local 환경·DB·오프라인·검토 토큰 보호 |
| `src/main/java/com/kkirikkiri/global/init/CommunitySeedDataService.java` | 기존 80개 글의 정확한 제목·본문 서명을 읽기 전용 제공 |
| `src/main/java/com/kkirikkiri/chat/service/ChatService.java` | 기존 검증을 공유하는 새 방 전용 생성. 기존 HTTP create-or-get 동작 유지 |
| `src/test/java/exhibition/ExhibitionDataIntegrationTest.java` | 실제 서비스·H2 통합 테스트 7개 |
| `src/test/java/exhibition/ExhibitionSafetyTest.java` | 실행 환경·출처·파일 잠금 보호 테스트 4개 |
| `build.gradle` | 서버 JAR에서 제외되는 exhibition source set과 컴파일 전용 prepareExhibition |
| `EXHIBITION_DATA.md` | 백업·미리보기·적용·복구·데이터 수정 절차와 한계 |
| `DEPLOYMENT.md` | 별도 전시회 도구 안내 연결 |
| 저장소 루트 `.gitignore` | 루트 실행 시에도 local-data 출처 파일 추적 방지 |

## 실행한 검증

| 검증 | 결과 |
| --- | --- |
| `gradlew.bat test bootJar prepareExhibition --console=plain` | 성공. 최종 실행 약 9분 27초 |
| 전체 테스트 XML 집계 | **87개 클래스, 1,502개 테스트, 실패 0·오류 0·건너뜀 0** |
| 전시회 테스트 | 통합 7개 + 보호 4개 = **11개 통과**. 전체 집계에 포함되며 별도 합산하지 않음 |
| `node backend/tools/sync-contract-docs.cjs --check` | 성공. Controller 73개 endpoint, record DTO 75개, Entity 30개 대조 |
| `git diff --check` | 성공 |
| 서버 bootJar ZIP 내용 확인 | `BOOT-INF/classes/exhibition/` 항목 **0개** |

전시회 테스트의 주요 확인 내용:

1. 미리보기 전후 전체 업무 테이블의 내용 지문이 같으며 출처 JSON도 생성하지 않는다.
2. 모든 시나리오 생성 후 실제 조회 서비스에서 프로필·설문·추천·게시글·익명 댓글·채팅·일정을 조회한다.
3. 동일 시나리오 재실행은 행·ID·시각·메시지·읽음 상태를 그대로 유지한다.
4. 기록된 이전 카탈로그의 게시글·일정 제목을 갱신해도 기존 ID를 유지하며 중복 생성하지 않는다.
5. 레거시 글의 원문과 작성자 출처를 확인해 기존 ID·작성 시각을 재사용한다.
6. 출처 없는 계정, 편집된 글, 외부 사진, 실제 댓글, 기존 관리자·대화·학교 일정·실제 회원 관련 매칭 행을 보존한다.
7. 시연 중 수동 변경은 SKIP하며 변경된 상태를 정상 출처로 재채택하지 않는다.
8. 핵심 단계의 예외는 회원·프로필·설문·글 변경을 롤백하고 출처 등록도 남기지 않는다.
9. 채팅방 독립 커밋 후 메시지 실패를 실제로 재현해 부분 적용 상태가 남음을 확인하고, 재실행과 중복 생성을 차단한다.
10. 두 동시 신규 생성 요청은 한 방만 만들며, 이미 있는 방을 새 방으로 채택하지 않는다. 기존 create-or-get은 유지한다.
11. prod/test/혼합/기본 프로필, 원격·다른 schema URL, CREATE/DELETE, 추가 프로필·JSON 설정, 검토 토큰 누락을 거절한다.
12. 출처 파일의 DB·기준일 불일치, 깨진 JSON, 미완료 상태, 동시 파일 잠금을 거절한다.

## 미검증·운영 적용 전 보류

- MySQL의 실제 DDL·대소문자·collation·InnoDB 잠금·GET_LOCK·실행계획·실제 적용은 미검증이다.
- 실제 브라우저 E2E, 공개 배포, 실제 학교 수집·SMTP·AI 호출을 실행하지 않았다. 프론트 변경은 없다.
- 현재 MySQL의 원래 시드 출처 파일과 회원·글이 실제로 일치하는지 확인하지 않았다.
  출처가 없으면 이메일만으로 채택하거나 새 출처를 만들어 강제 갱신하지 않는다.
- 기존 성별·출생연도·입학연도 및 기존 자격증명 변경은 보류한다. 새 실제 사용자 정책을 추가하지 않는다.
- 설문/선호 부모 ID는 유지하지만 옵션·가중치 자식 행은 기존 서비스의 교체 방식에 따라 ID가 재발급될 수 있다.
- 도구 전체는 단일 트랜잭션이 아니다. 실패 시 출처가 APPLYING으로 남으며 운영자가 DB와 출처 파일을
  같은 시점의 백업으로 복구해야 한다. 전체 복구 전에 적용 이후 실제 이용 기록 존재 여부를 확인해야 한다.
- 일반 조회 API를 가상 회원만으로 필터링하지 않는다. 공개 시연에 사용할 DB에 실제 데이터가 없는지는 별도 확인한다.

백업·미리보기·적용·복구 절차는 [EXHIBITION_DATA.md](EXHIBITION_DATA.md)에 제공했다.
