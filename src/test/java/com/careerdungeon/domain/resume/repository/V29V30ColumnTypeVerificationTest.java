package com.careerdungeon.domain.resume.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V29/V30이 실제로 컬럼 타입을 바꿨는지, {@code SHOW COLUMNS}로 직접 확인하는 테스트다.
 *
 * <p>이 테스트가 필요한 이유가 바로 이슈 #196의 본질이다 — V14/V28은 SQL 파일 텍스트에
 * 분명히 올바른 {@code ALTER TABLE ... MODIFY COLUMN} 구문이 있었고(파일 내용을 문자열로
 * 대조하는 테스트로는 절대 못 잡는다), Flyway 로그도 "성공"이라고 남겼지만, 실제 DB
 * 컬럼은 몇 번의 배포를 거치도록 전혀 바뀌지 않은 채로 남아있었다. 그래서 이 테스트는
 * SQL 파일 내용이나 Flyway 로그가 아니라, {@code information_schema}/{@code SHOW COLUMNS}로
 * **실제 DB에 물어봐서** 확인한다.
 *
 * <p>{@link ResumeTextColumnSizeTest}와 동일한 방식으로, 로컬 실제 MySQL이 있을 때만
 * (-DrunMigrationColumnTypeVerificationTest=true) 선택적으로 실행되도록 게이팅한다. 기본
 * {@code ./gradlew test}(H2)에는 포함되지 않는다 — H2는 TEXT/MEDIUMTEXT/ENUM/VARCHAR를
 * MySQL과 다르게 취급해서 이 검증 자체가 의미가 없다.
 *
 * <p>실행 방법: 로컬에 {@code application-local.yml}에 설정된 MySQL이 떠 있는 상태에서
 * {@code ./gradlew test --tests "*.V29V30ColumnTypeVerificationTest" -DrunMigrationColumnTypeVerificationTest=true}
 */
@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "runMigrationColumnTypeVerificationTest", matches = "true")
class V29V30ColumnTypeVerificationTest {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("V29 적용 후 resumes.extracted_text는 실제로 mediumtext다")
    void resumesExtractedTextIsActuallyMediumtext() throws Exception {
        assertThat(columnType("resumes", "extracted_text")).isEqualTo("mediumtext");
    }

    @Test
    @DisplayName("V30 적용 후 interview_sessions.selected_keyword는 실제로 varchar(20)다")
    void interviewSessionsSelectedKeywordIsActuallyVarchar20() throws Exception {
        assertThat(columnType("interview_sessions", "selected_keyword")).isEqualTo("varchar(20)");
    }

    private String columnType(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "show columns from " + table + " like '" + column + "'")) {
            resultSet.next();
            return resultSet.getString("Type");
        }
    }
}
