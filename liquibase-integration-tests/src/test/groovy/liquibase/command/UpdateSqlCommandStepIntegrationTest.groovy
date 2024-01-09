package liquibase.command

import liquibase.Scope
import liquibase.command.core.UpdateSqlCommandStep
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep
import liquibase.database.core.DatabaseUtils
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.extension.testing.testsystem.DatabaseTestSystem
import liquibase.extension.testing.testsystem.TestSystemFactory
import liquibase.extension.testing.testsystem.spock.LiquibaseIntegrationTest
import liquibase.statement.core.RawSqlStatement
import spock.lang.Shared
import spock.lang.Specification

@LiquibaseIntegrationTest
class UpdateSqlCommandStepIntegrationTest extends Specification{

    @Shared
    private DatabaseTestSystem postgres = Scope.currentScope.getSingleton(TestSystemFactory).getTestSystem("postgresql") as DatabaseTestSystem

    def "validate UpdateSql only generates SQL statement to set SEARCH_PATH once"() {
        when:
        def postgresDB = postgres.getDatabaseFromFactory()
        final Executor executor = Scope.getCurrentScope().getSingleton(ExecutorService.class).getExecutor("jdbc", postgresDB)
        def catalogName = postgres.getConnection().getCatalog()
        def schemaName = postgres.getConnection().getSchema()
        def searchPath = DatabaseUtils.getFinalPostgresSearchPath(executor, catalogName, schemaName, postgresDB)

        def outputStream = new ByteArrayOutputStream()
        def updateSqlCommand = new CommandScope(UpdateSqlCommandStep.COMMAND_NAME)
        updateSqlCommand.addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, postgresDB)
        updateSqlCommand.addArgumentValue(UpdateSqlCommandStep.CHANGELOG_FILE_ARG, "liquibase/update-tests.yml")
        updateSqlCommand.setOutput(outputStream)
        updateSqlCommand.execute()
        def generatedSql = outputStream.toString()

        then:
        countSetSearchPathOccurrences(generatedSql, String.format("SET SEARCH_PATH TO %s, %s;", catalogName, searchPath)) == 0
        countSetSearchPathOccurrences(generatedSql, String.format("ALTER DATABASE %s SET SEARCH_PATH TO %s;", catalogName, searchPath)) == 1
    }

    private int countSetSearchPathOccurrences(String updateSqlOutput, String searchPathSetup) {
        int count = 0;
        int index = updateSqlOutput.indexOf(searchPathSetup)

        while (index != -1) {
            count++;
            index = updateSqlOutput.indexOf(searchPathSetup, index + 1)
        }
        return count
    }
}
