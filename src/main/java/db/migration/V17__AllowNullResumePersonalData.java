package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V17__AllowNullResumePersonalData extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        boolean h2 = "H2".equals(context.getConnection().getMetaData().getDatabaseProductName());

        try (Statement statement = context.getConnection().createStatement()) {
            if (h2) {
                statement.execute("ALTER TABLE resumes ALTER COLUMN s3_key VARCHAR(255) NULL");
                statement.execute("ALTER TABLE resumes ALTER COLUMN file_hash VARCHAR(64) NULL");
                return;
            }

            statement.execute("ALTER TABLE resumes MODIFY s3_key VARCHAR(255) NULL");
            statement.execute("ALTER TABLE resumes MODIFY file_hash VARCHAR(64) NULL");
        }
    }
}
