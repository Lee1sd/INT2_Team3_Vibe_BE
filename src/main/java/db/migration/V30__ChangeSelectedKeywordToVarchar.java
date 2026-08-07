package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * V14({@code V14__change_selected_keyword_to_varchar.sql})도 V28과 동일한 MySQL 전용
 * 조건부 주석 트릭을 썼는데, Flyway의 SQL 파서가 그 구문을 실행 대상이 아니라 그냥 주석으로
 * 걸러내 실제로는 한 번도 적용된 적이 없었다(이슈 #196 조사 중 확인). 로컬에서 직접 확인한
 * 결과 {@code interview_sessions.selected_keyword}는 지금도 여전히 {@code ENUM}이고,
 * {@code spring.jpa.hibernate.ddl-auto: validate}(운영)는 이 불일치도 V28과 마찬가지로
 * 기동 실패로 이어진다:
 * {@code Schema-validation: wrong column type encountered in column [selected_keyword]
 * in table [interview_sessions]; found [enum (Types#CHAR)], but expecting
 * [varchar(20) (Types#VARCHAR)]}.
 *
 * <p>V29와 동일하게, V17 패턴(런타임에 DB 종류를 확인해 MySQL에만 JDBC
 * {@code Statement.execute()}로 DDL을 직접 실행)으로 다시 적용한다. 이미 배포된 V14는
 * {@code flyway_schema_history}에 적용 완료로 기록돼 있어 재실행되지 않고, 팀 규칙상
 * 머지된 {@code Vn} 파일 수정도 금지되므로 새 마이그레이션으로 해결한다.
 */
public class V30__ChangeSelectedKeywordToVarchar extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();

        // H2는 V14 원본 SQL이 애초에 파싱 불가라 실행된 적이 없고, 엔티티가 이미
        // 일반 VARCHAR(String) 매핑이라 H2 스키마는 처음부터 문제 없다.
        if ("H2".equals(product)) {
            return;
        }

        // "H2가 아니면 MySQL이다"라고 가정하지 않는다 — MySQL을 명시적으로 확인하고,
        // 그 외 DB에서는 원인을 알 수 있게 실패시킨다(V29와 동일한 이유).
        if (!"MySQL".equals(product)) {
            throw new IllegalStateException(
                    "V30__ChangeSelectedKeywordToVarchar는 MySQL과 H2만 지원합니다. "
                            + "감지된 DB: " + product);
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE interview_sessions MODIFY COLUMN selected_keyword VARCHAR(20) NOT NULL");
        }
    }
}
