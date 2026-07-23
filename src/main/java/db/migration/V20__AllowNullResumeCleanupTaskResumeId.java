package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V20__AllowNullResumeCleanupTaskResumeId extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        boolean h2 = "H2".equals(context.getConnection().getMetaData().getDatabaseProductName());
        try (Statement statement = context.getConnection().createStatement()) {
            if (h2) {
                statement.execute("ALTER TABLE resume_file_cleanup_tasks ALTER COLUMN resume_id BIGINT NULL");
            } else {
                statement.execute("ALTER TABLE resume_file_cleanup_tasks MODIFY resume_id BIGINT NULL");
            }
        }
    }
}
