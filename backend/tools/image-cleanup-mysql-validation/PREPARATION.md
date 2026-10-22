# 이미지 정리 MySQL 도구 준비 결과

기준일: 2026-10-22, Asia/Seoul.

**도구 구현 및 DB 없는 준비 완료. 실제 MySQL 시나리오 실행 0건, JDBC 접속 0건.**
실제 Worker 기능·MySQL 잠금·재시도·재시작을 통과했다고 판정하지 않는다.
자원 생성·DB 접속·실제 실행·정리는 사용자 승인 후 별도 단계다.

## 파일과 구현 범위

신규 파일은 이 디렉터리의 아래 5개다.

- `ImageCleanupMysql.java`: 13개 시나리오, 신규 DB/계정 준비 단계, 별도 JDBC 상태·파일 증거,
  실제 JVM 종료/새 Scheduler JVM, 결과 집계.
- `ImageCleanupRuntime.java`: 운영 Repository/Service/Worker/Storage/Scheduler를 연결하는
  최소 JPA 컨텍스트, 실제 SQL 격리/오류/잠금 관찰과 파일 제어 지점.
- `run.ps1`: DB 없는 준비와 명시적 승인 실행 분리, CCRA 보조 코드 사본, 격리 자원 및 소유권 검사.
- `README.md`: 시나리오·재사용·환경·정확한 실행/정리 방법.
- `PREPARATION.md`: 이번 결과 및 남은 조건.

운영 소스·기존 테스트·기존 실행기·Gradle·기존 마이그레이션은 수정하지 않았다.
일반 이미지 CRUD·CCRA 24개·기존 H2 테스트를 재실행하지 않았다.

## DB 없는 검사

- Java/Javac 21.0.9로 기존 bootJar의 클래스/라이브러리를 사용한 도구 컴파일 성공.
- PowerShell 구문 검사 성공.
- PREPARE 모드의 클래스 연결·13개 고유 사례 카탈로그 검사 성공.
- Spring Bean 정의 파싱에서 이미지 Repository 2개를 확인했다.
  Scheduler OFF는 0개, ON은 1개로 확인했다. 컨텍스트 refresh 및 DB 인프라 Bean 생성은 하지 않았다.
- 계약 정적 검사 성공: 73 endpoints / 75 record DTOs / 30 entities.
- 기존 추적 파일의 Git diff는 없으며 신규 파일은 미추적 상태로 준비했다. 커밋·푸시·병합 없음.

기존 JAR SHA-256:
`A6FEF54637F9C392FA76B46227F706A9EC70FA0CD3513360C730909E323CEF79`.
기존 JAR를 재빌드하거나 이를 현재 SHA에서 재실행한 것으로 표시하지 않는다.

재개 후 첫 준비 검사에서 Java 버전 stderr의 PowerShell 처리와 import 누락을 수정했다.
DB 없는 Spring metadata 검사에서는 검사기 Environment 전달을 보완했다.
이들은 준비 도구 오류이며 MySQL 기능 실패/통과로 집계하지 않는다.

기계 판독 근거는 Git 제외 `backend/build/image-cleanup-mysql-validation/`의
`prepare.json`, `compile.log`, `source-hashes.json`, `environment-preflight.json`에 둔다.
compile.log가 비어 있는 것은 컴파일 종료 코드 0 및 진단 없음과 함께 해석한다.

## 읽기 전용 환경 확인

2026-10-22 10:54 KST에 로컬 상태를 조회했다.

| 항목 | 확인 결과 |
| --- | --- |
| 포트 13336 | 활성 TCP listener 없음 |
| Docker context endpoint | `npipe:////./pipe/dockerDesktopLinuxEngine` |
| Docker 엔진 | 해당 파이프를 찾을 수 없어 연결 불가 |
| 컨테이너 이름 충돌 | **현재 확인 보류** — 엔진 목록 조회 실패 |
| 요청 이미지의 현재 존재/아키텍처 | **현재 확인 보류** — 이미지 조회 실패 |
| 자원 변경 | 없음. 엔진 시작/재시작·설정 변경·pull·생성/삭제 없음 |

앞선 날짜에 확인한 이미지/이름 상태를 현재 상태로 승계하지 않는다.
환경이 준비된 뒤 `-PrepareOnly -CheckEnvironment`를 성공시켜야 한다.
승인 실행 명령도 같은 검사 실패 시 생성 전에 중단한다.

## 실제 실행 전 남은 조건과 판정

1. 사용자가 Docker 엔진을 사용할 수 있는 상태로 준비하고, 이름·포트·이미지 확인을 통과해야 한다.
2. 신규 컨테이너·실행별 DB/계정 생성 및 해당 자원 정리에 대한 사용자 승인이 필요하다.
3. 초기 새 빈 schema의 Entity DDL·runtime 최소 권한·실제 SQL과 Worker 실행은 아직 미검증이다.
4. 잠금 후 기한 재검사 누락은 관찰 시 FINDING으로 남기며 PASS에 포함하지 않는다.
   이번 준비만으로 실제 재현이나 수정 완료를 주장하지 않는다.
5. 실제 파일 삭제 직후 Error 모사와 실제 JVM 종료/재시작은 다른 사례로 기록한다.

## 생성 계획과 영향

- 컨테이너: `kkiri-image-cleanup-mysql-20261021` 1개, `mysql:8.0.46-oraclelinux9`.
- 포트: `127.0.0.1:13336`만 공개. HTTP/외부 연동 서버 없음.
- 실행별 DB `kkiriimgcleanup<12 hex>`, 계정 `imgcleanup_<동일 hex>@%`를 신규 생성한다.
- 새 tmpfs 데이터 공간만 사용한다. 기존 볼륨·컨테이너·DB·계정 재사용 및 초기화 없음.
- root는 신규 준비에만 사용한다. 업무 계정의 초기 DDL 권한은 회수하고,
  runtime은 해당 DB DML 4종과 잠금 관찰 SELECT만 유지한다.
- 파일 쓰기·삭제는 실행별 build 하위 이미지 루트로 제한한다.
  CPU 2개/메모리 1GiB, 앱 heap 512MiB/풀 8개를 상한으로 둔다.
- 승인 실행의 finally는 name/ID/owner가 일치하는 생성 컨테이너만 제거한다.
  신규 DB/계정/tmpfs 데이터는 함께 제거되며 로컬 증거와 마지막 파일은 보존한다.

현재 생성 자원은 0개다. 환경 확인 보류와 실제 실행 승인 대기 상태다.
정확한 명령과 결과 파일 설명은 [README](README.md)를 따른다.
