package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/**
 * V24에서 바뀐 4+1 문항·문항당 20점 계약을 DB 컬럼 설명에도 반영한다.
 * 이미 배포된 V1 SQL의 체크섬을 유지하면서 MySQL과 테스트용 H2 문법 차이를 처리한다.
 */
public class V25__UpdateJudgmentColumnComments extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        boolean h2 = "H2".equals(context.getConnection().getMetaData().getDatabaseProductName());

        try (Statement statement = context.getConnection().createStatement()) {
            if (h2) {
                updateH2Comments(statement);
                return;
            }
            updateMySqlComments(statement);
        }
    }

    /** H2에서는 표준 COMMENT ON COLUMN 문법으로 컬럼 설명만 갱신한다. */
    private void updateH2Comments(Statement statement) throws Exception {
        statement.execute("COMMENT ON COLUMN answer_scores.session_id IS "
                + "'세션당 최대 5건 (최초 질문4+꼬리질문1)'");
        statement.execute("COMMENT ON COLUMN answer_scores.turn IS '1~5 (꼬리질문은 5)'");
        statement.execute("COMMENT ON COLUMN answer_scores.score IS '문항당 0~20점'");
        statement.execute("COMMENT ON COLUMN judgment_results.total_score IS "
                + "'5문항 합산 (0~100). 결과 화면에는 총점을 그대로 표시'");
        statement.execute("COMMENT ON COLUMN judgment_results.passed IS '세션 레벨별 합격선 충족 여부'");
        statement.execute("COMMENT ON COLUMN judgment_results.overall_feedback IS "
                + "'최초 질문4+꼬리질문1 종합 최종 커리어 리포트, 사용자 노출'");
    }

    /** MySQL에서는 컬럼 정의를 유지한 채 COMMENT 절을 포함해 설명을 갱신한다. */
    private void updateMySqlComments(Statement statement) throws Exception {
        statement.execute("ALTER TABLE answer_scores MODIFY COLUMN session_id BIGINT NOT NULL "
                + "COMMENT '세션당 최대 5건 (최초 질문4+꼬리질문1)'");
        statement.execute("ALTER TABLE answer_scores MODIFY COLUMN turn INT NOT NULL "
                + "COMMENT '1~5 (꼬리질문은 5)'");
        statement.execute("ALTER TABLE answer_scores MODIFY COLUMN score INT NOT NULL "
                + "COMMENT '문항당 0~20점'");
        statement.execute("ALTER TABLE judgment_results MODIFY COLUMN total_score INT NOT NULL "
                + "COMMENT '5문항 합산 (0~100). 결과 화면에는 총점을 그대로 표시'");
        statement.execute("ALTER TABLE judgment_results MODIFY COLUMN passed BOOLEAN NOT NULL "
                + "COMMENT '세션 레벨별 합격선 충족 여부'");
        statement.execute("ALTER TABLE judgment_results MODIFY COLUMN overall_feedback TEXT NOT NULL "
                + "COMMENT '최초 질문4+꼬리질문1 종합 최종 커리어 리포트, 사용자 노출'");
    }
}
