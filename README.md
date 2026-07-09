# Career Dungeon Backend

Career Dungeon 백엔드는 이력서 기반 면접 흐름을 관리하기 위한 Spring Boot API 서버입니다. 현재 레포는 인증, 이력서, 면접, 페르소나, 진행 상태, 메시지, 답변 평가 도메인을 기준으로 패키지가 분리되어 있으며, 공통 설정과 보안/예외/유틸 기능은 `global` 영역에서 관리합니다.

## 기술 스택

| 구분 | 기술 | 버전 | 용도 |
| --- | --- | --- | --- |
| Language | Java | 17 | 애플리케이션 개발 언어 |
| Build Tool | Gradle Wrapper | 8.14.5 | 빌드, 테스트, 패키징 |
| Framework | Spring Boot | 3.5.16 | 백엔드 애플리케이션 프레임워크 |
| Dependency Management | Spring Dependency Management Plugin | 1.1.7 | Spring/외부 의존성 버전 관리 |
| Core Framework | Spring Framework | 6.2.19 | DI, MVC, 트랜잭션 등 Spring 기반 기능 |
| Web | Spring Web MVC, Embedded Tomcat | Spring Boot 3.5.16, Tomcat 10.1.55 | REST API와 내장 WAS |
| Validation | Spring Validation, Hibernate Validator | Spring Boot 3.5.16, Hibernate Validator 8.0.3.Final | 요청 값 검증 |
| ORM | Spring Data JPA, Hibernate ORM | Spring Data JPA 3.5.13, Hibernate ORM 6.6.53.Final | 데이터 접근과 ORM |
| Database Driver | MySQL Connector/J | 9.7.0 | MySQL 연결 |
| Connection Pool | HikariCP | 6.3.3 | DB 커넥션 풀 |
| Security | Spring Security, OAuth2 Client | Spring Security 6.5.11 | 인증/인가, Google OAuth2 연동 |
| JWT | JJWT | 0.13.0 | JWT 생성과 검증 |
| File Parsing | Apache PDFBox | 3.0.7 | PDF 이력서 텍스트 추출 |
| Storage | AWS SDK for Java S3 | 2.47.1 | AWS S3 파일 저장 연동 |
| Retry | Spring Retry, Spring AOP | Spring Retry 2.0.13, Spring Boot AOP 3.5.16 | 외부 API 호출 재시도 |
| Test | Spring Boot Test, JUnit Jupiter | Spring Boot Test 3.5.16, JUnit Jupiter 5.12.2 | 단위/통합 테스트 |
| Test DB | H2 Database | 2.3.232 | 테스트용 인메모리 DB |
| Test Container | Testcontainers | 1.21.4 | MySQL 컨테이너 기반 테스트 지원 |

## 프로젝트 구조

```text
.
├── .github/
│   ├── workflows/ci.yml              # PR/Push 시 Gradle check와 bootJar를 실행하는 CI 설정
│   ├── ISSUE_TEMPLATE/               # 이슈 템플릿
│   └── PULL_REQUEST_TEMPLATE.md      # PR 템플릿
├── gradle/wrapper/                   # Gradle Wrapper 실행 파일과 배포 설정
├── src/
│   ├── main/
│   │   ├── java/com/careerdungeon/
│   │   │   ├── CareerDungeonApplication.java
│   │   │   ├── domain/
│   │   │   │   ├── auth/             # 인증, OAuth, 사용자 인증 데이터
│   │   │   │   ├── resume/           # 이력서 업로드, 파싱, 저장, 조회
│   │   │   │   ├── interview/        # 면접 진행 유스케이스와 데이터
│   │   │   │   ├── persona/          # 면접관 페르소나 모델
│   │   │   │   ├── progress/         # 면접 단계와 진행 상태
│   │   │   │   ├── message/          # 면접 메시지 모델
│   │   │   │   └── judgment/         # 면접 답변 평가와 판단 결과
│   │   │   └── global/
│   │   │       ├── common/           # 공통 모델과 상수
│   │   │       ├── config/           # 전역 Bean과 외부 연동 설정
│   │   │       ├── exception/        # 전역 예외와 예외 응답 처리
│   │   │       ├── security/         # 인증/인가 보안 정책
│   │   │       └── util/             # 도메인 독립 공통 유틸
│   │   └── resources/
│   │       ├── application.yml       # 공통 Spring 설정
│   │       └── application-local.yml # 로컬 개발 환경 설정
│   └── test/
│       ├── java/                     # 테스트 코드
│       └── resources/
│           └── application-test.yml  # H2 기반 테스트 설정
├── .env.example                      # 로컬 환경 변수 예시
├── build.gradle                      # 플러그인, 의존성, Java toolchain 설정
├── gradle.properties                 # Gradle 캐시와 JVM 옵션
├── gradlew / gradlew.bat             # OS별 Gradle Wrapper 실행 파일
└── settings.gradle                   # Gradle 루트 프로젝트 이름 설정
```

## 패키지 설계

도메인 패키지는 `controller`, `service`, `repository`, `entity`, `dto` 계층을 기준으로 확장할 수 있도록 구성되어 있습니다.

| 패키지 | 역할 |
| --- | --- |
| `domain.auth` | 인증 API, OAuth 공급자 연동, JWT 기반 인증 흐름, 인증 데이터 관리 |
| `domain.resume` | 이력서 API, 이력서 파일 파싱, 이력서 데이터 저장/조회 |
| `domain.interview` | 면접 API, 면접 유스케이스, 면접 데이터 저장/조회 |
| `domain.persona` | 면접관 페르소나 정의와 관련 비즈니스 모델 |
| `domain.progress` | 면접 진행 상태와 단계 전환 |
| `domain.message` | 면접 과정에서 사용하는 메시지 모델 |
| `domain.judgment` | 면접 답변 평가와 판단 결과 |
| `global.config` | 전역 Bean, 외부 연동 설정, 재시도 설정 |
| `global.security` | 애플리케이션 전역 보안 정책 |
| `global.exception` | 공통 예외 타입과 예외 응답 처리 |
| `global.common` | 여러 도메인에서 공유하는 모델과 상수 |
| `global.util` | 특정 도메인에 종속되지 않는 보조 기능 |

## 환경 변수

로컬 실행은 루트 경로의 `.env` 파일을 사용합니다. `.env.example`을 복사한 뒤 실제 값으로 변경합니다.

```bash
cp .env.example .env
```

| 변수 | 기본값/예시 | 설명 |
| --- | --- | --- |
| `DB_HOST` | `localhost` | MySQL 호스트 |
| `DB_PORT` | `3306` | MySQL 포트 |
| `DB_NAME` | `career_dungeon` | 사용할 데이터베이스 이름 |
| `DB_USERNAME` | `root` | DB 사용자 |
| `DB_PASSWORD` | `change-me` | DB 비밀번호 |
| `DB_POOL_SIZE` | `10` | HikariCP 최대 커넥션 수 |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL 자동 적용 옵션 |
| `JPA_SHOW_SQL` | `true` | SQL 로그 출력 여부 |
| `GOOGLE_CLIENT_ID` | `change-me` | Google OAuth2 클라이언트 ID |
| `GOOGLE_CLIENT_SECRET` | `change-me` | Google OAuth2 클라이언트 Secret |
| `JWT_SECRET` | `replace-with-a-base64-encoded-secret-of-at-least-32-bytes` | JWT 서명용 Base64 Secret |
| `AWS_REGION` | `ap-northeast-2` | AWS 리전 |
| `AWS_S3_BUCKET` | `change-me` | S3 버킷 이름 |
| `AWS_ACCESS_KEY_ID` | `change-me` | AWS Access Key |
| `AWS_SECRET_ACCESS_KEY` | `change-me` | AWS Secret Key |

## 실행 방법

기본 프로필은 `local`입니다. 로컬 실행 전 MySQL 데이터베이스와 `.env` 파일을 준비합니다.

```bash
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

## 빌드와 테스트

```bash
./gradlew clean build
./gradlew test
./gradlew check bootJar
```

CI는 GitHub Actions에서 Java 17 Temurin 환경으로 `./gradlew check bootJar --no-daemon --stacktrace`를 실행합니다.

## 설정 파일

| 파일 | 설명 |
| --- | --- |
| `src/main/resources/application.yml` | 애플리케이션 이름, `.env` import, 기본 프로필, JPA Open Session In View 비활성화, multipart 10MB 제한 |
| `src/main/resources/application-local.yml` | `.env` 기반 MySQL 연결, HikariCP, JPA DDL/SQL 로그 설정 |
| `src/test/resources/application-test.yml` | 외부 MySQL 없이 테스트할 수 있는 H2 MySQL 호환 모드 설정 |

## 개발 메모

- Java toolchain은 17로 고정되어 있습니다.
- Gradle configuration cache와 build cache가 활성화되어 있습니다.
- 파일 업로드 제한은 단일 파일과 전체 요청 모두 10MB입니다.
- `RetryConfig`에서 `@EnableRetry`를 활성화해 LLM 또는 외부 API 호출 재시도 처리를 지원합니다.
