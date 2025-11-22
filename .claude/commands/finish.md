# /finish

Run the development workflow completion tasks.

## Steps to execute:

1. Run `./gradlew check` (or the module's equivalent lint task) to validate formatting and static analysis.
2. Run `./gradlew test` to execute the test suite.
3. Run `./gradlew build` to ensure the project packages successfully.

All three commands must pass successfully before considering the work complete. If any command fails, fix the issues before proceeding to the next command.
