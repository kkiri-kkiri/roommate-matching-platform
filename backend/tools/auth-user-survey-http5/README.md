# Auth · User · Survey 5단계 HTTP 실행 도구

2026-10-22 사용자 승인 범위 실행 완료. **P0 13 PASS / P1 46 PASS / 실제 TCP HTTP 183건.**
초기 무효 만료 fixture 2회와 P0 검토 JSON 오류 1회를 포함한 이력·한계는
[완료 보고서](../../AUTH_USER_SURVEY_HTTP5_VALIDATION.md)와
[실제 결과 JSON](../../validation/auth-user-survey-http5-20261022.json)에 남겼다.
[승인 전 계획](../../validation/auth-user-survey-http5-plan-20261022.json)은 당시 NOT RUN 기록으로 보존한다.

## 보존 환경 — 재생성·전체 재실행 금지

| 항목 | 현재 대상 |
| --- | --- |
| 컨테이너 / 볼륨 | kkiri-aus-http5-mysql-20261022 / kkiri-aus-http5-data-20261022 |
| DB / 계정 | kkiriaus5http20261022 / aus5http20261022@% |
| MySQL 바인딩 | 127.0.0.1:13327 → 3306 |
| MySQL / UUID | 8.0.46 / cae1bfa1-cdea-11f1-a074-76682699272e |
| hostname | kkiri-aus-http5-mysql-20261022 |
| 이미지 | mysql:8.0.46-oraclelinux9, ID sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b |
| HTTP | 127.0.0.1:18093, 모든 소유 JVM 종료·listener 0 |
| 최종 DB | 31개 테이블 모두 0행, DDL 유지 |
| 자원 상태 | 컨테이너 running, 볼륨·DB·계정·스키마 보존 |

**Provision/Execute/Continue를 다시 실행하지 않는다.** 이미 자원이 있고 일회용 실행 marker가 존재한다.
삭제·초기화·DROP/TRUNCATE 경로는 제공하지 않는다. marker나 manifest를 지워 실행 제한을 우회하지 않는다.
새 환경 생성·예기치 않은 환경 변경·자원 삭제가 필요하면 별도 계획·승인을 받는다.
2026-10-21의 기존 보존 컨테이너/5개 DB와 운영 자원은 이 도구의 접속 대상이 아니다.

## 읽기 전용 또는 로컬 확인

저장소 루트에서 실행한다. Java 21, 현재 JAR/main 클래스/resources와 로컬 Docker Linux 엔진이 필요하다.

```powershell
# 로컬 생성 소스·의존 라이브러리·클래스·사례 목록만 준비. DB/HTTP/Docker 연결 없음.
powershell -NoProfile -File backend/tools/auth-user-survey-http5/run.ps1 -Stage Prepare

# 보존한 새 DB의 identity·권한·행 수·DDL·시간 변환만 읽기. DB 변경 없음.
powershell -NoProfile -File backend/tools/auth-user-survey-http5/run.ps1 -Stage Audit -ApprovedIsolatedRun

# 로컬 원본 이력과 최종 감사를 대조하여 비밀 없는 Git용 결과 JSON 갱신.
node backend/tools/auth-user-survey-http5/report.cjs

node backend/tools/sync-contract-docs.cjs --check
```

Audit는 build/auth-user-survey-http5/environment.json을 필수로 사용한다.
파일이 없거나 UUID/컨테이너 ID/이미지/바인딩/볼륨이 다르면 중단한다. 보고서 값을 복사해 임의 연결하지 않는다.
새 컨테이너 메타데이터에서 자격증명을 메모리로만 읽고 전용 환경으로 전달한다.
원문 메타데이터·환경 변수·비밀번호를 출력하지 않는다. 기존 DB 계정 자격증명은 읽지 않는다.
스위치는 향후 다른 자원에 대한 사용자 승인을 대신하지 않는다.

## 도구 구성과 실제 실행 이력

- prepare.cjs: 기존 User HTTP 서버·JWT 로그인·메일 메모리 대체·SQL 관찰 기능과 SurveyTestData 재사용.
  원본은 그대로 두고 build/의 생성본만 새 고정 대상에 맞춘다. 기존 전체 실행/보존 DB snapshot/root 생성 진입점은 차단한다.
- AusHttp5.java: 승인된 59개 사례, P0 결과 검토 gate, 별도 JDBC 상태 확인, 종료 후 새 DB 데이터 정리.
- AusHttp5Audit.java: DB 행을 변경하지 않는 감사·시간 변환 진단.
- provision.ps1: 새 고정 이름/포트/이미지·로컬 엔진을 검사하고 새 자원만 1회 생성했다.
- run.ps1: Prepare / Execute / 역사적 Continue / Audit. DB 연결마다 identity·권한을 검사한다.
- review.cjs: 승인 전 기존 1,502건과 계획을 대조했다. 생성 후 과거 계획 덮어쓰기를 거절한다.
- report.cjs: 4회 원본 실행·서버 관찰 수, 59개 최종 상태, 183 HTTP, 265개 운영 파일 지문, 최종 빈 DB/DDL을 대조한다.

실제 순서:

1. 새 자원 이름 부재·포트 충돌 없음·로컬 MySQL 이미지 확인, 승인된 새 자원 생성.
2. 빈 DB에 31개 테이블 DDL 1회 적용, Boot는 validate/SQL never로 기동.
3. availability 8 PASS. 만료 fixture의 시간 매핑 오류에서 중단, HTTP 종료 후 새 DB 0행 정리.
4. 읽기 전용 진단으로 LocalDateTime 직접 바인딩과 Hibernate Timestamp 변환의 9시간 차이 확인.
5. 첫 보정의 엄격한 시각 비교가 검증 HTTP 전에 실패해 중단·정리. 원본 유지.
6. 명시적 Timestamp·고정 과거 초·유효 만료 사전 검사로 보완. DDL 재적용 없이 빈 31개 테이블 재사용.
7. 미완료 P0 5 PASS로 합계 13 PASS, 결과 검토. 검토 파일 UTF-8 BOM 때문에 gate에서 중단·정리.
8. BOM 입력 처리만 보완하고 동일 스키마 재사용. 검토된 P0 13개를 반복하지 않고 P1 46 PASS.
9. 소유 HTTP JVM 종료·pool close 확인 후 합성 행만 DELETE. 별도 JDBC 감사로 31개 테이블 0행/DDL 보존 재확인.
10. HTTP listener 0, 새 MySQL 컨테이너/볼륨/DB/계정/스키마 보존 확인.

Continue는 위의 알려진 실행기 오류 3개 이력만 허용한 한정 복구 경로였다.
모든 복구 marker를 보존하며 이미 소비한 경로는 재실행할 수 없다. 일반 재실행 옵션이 아니다.
기존 성공 availability/P0 사례는 승계했으며 기존 1,502건은 재실행하지 않았다.

## 검증·정리 범위와 한계

새 DB에만 합성 이메일/닉네임/난수 비밀번호를 사용했다.
만료 fixture는 해당 행 1개에 고정 과거 시각을 설정하고 Hibernate가 읽는 유효 값이 과거임을 확인했다.
불완전 설문은 정상 HTTP 저장 후 해당 fixture의 완료 플래그/답변/선택지만 변경했다.
각 시험 요청은 실제 TCP HTTP → Spring Boot → MySQL 경로다.
정상 로그인·발급·저장·조회는 새 경계의 fixture/검증 요청으로 포함하며 별도 신규 기능 PASS로 부풀리지 않았다.

종료한 소유 JVM 확인 후 해당 새 DB의 fixture·Auth 제한/identity·합성 기준정보 행만 FK 역순 DELETE했다.
DB/계정/테이블/볼륨/컨테이너는 삭제하지 않았다. 복구 때도 DDL을 적용하거나 자원을 바꾸지 않았다.
별도 검증 프로필, ddl-auto=validate, SQL init never, 시드·수집·스케줄 비활성을 유지했다.
실제 SMTP sender는 메모리로 대체하고 AI base URL도 loopback 비접속 대상으로 고정했다.
업무 Service/Controller/DTO/Entity/설정/마이그레이션·다른 도메인 코드는 수정하지 않았다.

실제 TTL 경과는 **NOT RUN**, 실제 SMTP·HTTPS·ingress는 승인된 환경이 없어 각각 **BLOCKED**다.
fixture 만료 통과를 실제 TTL/배포 시간 동기화나 외부 메일 전달 검증으로 해석하지 않는다.

backend/build/auth-user-survey-http5/에는 로컬 생성물·비밀 없는 identity manifest와 원본 근거가 있다.
요청/서버 기록에는 상태·오류 코드·마스킹된 관찰 정보만 기록하며 응답 본문·비밀번호·JWT·토큰·메일 원문은 남기지 않는다.
이 폴더는 Git 제외다. Git 대상 신규 11개 및 .gitignore 수정과 상세 결과는 완료 보고서에서 확인한다.
