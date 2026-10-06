# Java code style

MES Base uses one repository-controlled, IDEA-like Java formatting path:

- `.editorconfig` supplies UTF-8, LF, final newline, four-space indentation, eight-space continuation indentation, and a 120-column guidance limit.
- `config/java-formatter.xml` is the committed formatter profile.
- The parent `pom.xml` owns Spotless for all Maven modules. Spotless uses the committed Eclipse formatter profile because it is portable in Maven and configured to match the project's IDEA Java style rather than google-java-format.
- Spotless checks the configured Java source set; historical Java must be migrated deliberately rather than hidden by a second formatter rule.

Run from the repository root:

```sh
mvn -B -ntp spotless:apply
mvn -B -ntp spotless:check
mvn -B -ntp test
git diff --check
```

Review formatter output before committing and do not include unrelated historical Java files. CI is not currently present; `spotless:check` is bound to Maven `verify`, so a future CI job can run `mvn -B -ntp verify` without an auto-fix step.

## Git hooks

Install the versioned hooks once per checkout:

```sh
./scripts/install-git-hooks
```

The `pre-commit` hook formats only staged Java files and stages the formatted result again. The `pre-push` hook runs `spotless:check` without modifying files. Hooks are local safeguards and can be skipped with Git options, so CI or a protected-branch check remains the final shared gate when CI is added.
