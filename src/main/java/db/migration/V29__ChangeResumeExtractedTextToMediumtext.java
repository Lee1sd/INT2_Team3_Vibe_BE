package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * V28(MySQL 전용 조건부 주석 구문으로 감싼 SQL 마이그레이션, {@code V28__change_resume_
 * extracted_text_to_mediumtext.sql} 참고)이 실제로는 컬럼을 바꾸지 못했다(이슈 확인, 로컬
 * 재현 완료). Flyway의 SQL 스크립트 파서가 그 조건부 주석 구문을 MySQL 서버로 보내기 전
 * 단계에서 이미 "그냥 주석"으로 걸러내는 것으로 보이며, 이는 H2뿐 아니라 실제 MySQL에서도
 * 동일하게 발생한다 — 그래서 {@code flyway_schema_history}에는 V28이 {@code success=true}로
 * 남지만, 실제 {@code resumes.extracted_text} 컬럼은 여전히 {@code TEXT}로 남는다(V14도
 * 동일한 방식으로 실패했음을 확인했다 — {@code interview_sessions.selected_keyword}가
 * 여전히 ENUM이다).
 *
 * <p>이미 배포된 V28은 {@code flyway_schema_history}에 적용 완료로 기록돼 있어 재실행되지
 * 않고, 팀 규칙상 머지된 {@code Vn} 파일도 수정할 수 없다({@code docs/operations/flyway-
 * migration-guide.md} §2). 그래서 V17과 동일한, 이미 검증된 패턴대로 Java 마이그레이션으로
 * 다시 시도한다 — SQL 주석 트릭을 전혀 쓰지 않고 JDBC {@code Statement.execute()}로 DDL을
 * 직접 실행하므로 Flyway의 SQL 파서가 끼어들 여지가 없다.
 */
public class V29__ChangeResumeExtractedTextToMediumtext extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();

        // H2는 TEXT/MEDIUMTEXT 둘 다 길이 제한 없는 CLOB으로 취급하므로 바꿀 필요가 없다.
        if ("H2".equals(product)) {
            return;
        }

        // "H2가 아니면 MySQL이다"라고 가정하지 않는다 — 이 프로젝트가 실제로 지원하는 DB는
        // MySQL(운영)과 H2(테스트)뿐이므로, MySQL을 명시적으로 확인하고 그 외에는 원인을
        // 알 수 있게 실패시킨다. 이렇게 하지 않으면 지원하지 않는 DB에서 MySQL 전용
        // `MODIFY COLUMN` 구문이 그대로 실행 시도돼 원인 불명의 실패로 이어질 수 있다.
        if (!"MySQL".equals(product)) {
            throw new IllegalStateException(
                    "V29__ChangeResumeExtractedTextToMediumtext는 MySQL과 H2만 지원합니다. "
                            + "감지된 DB: " + product);
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE resumes MODIFY COLUMN extracted_text MEDIUMTEXT NULL");
        }
    }
}
