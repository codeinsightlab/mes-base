# Java code style

MES Base uses the IntelliJ IDEA native Java formatter:

- `.editorconfig` supplies UTF-8, LF, final newline, four-space indentation, eight-space continuation indentation, and a 120-column guidance limit.
- `.idea/codeStyles/Project.xml` is the committed project-level IDEA Code Style.
- `scripts/format-java` is the repository entry point and calls IntelliJ IDEA's native `format.sh`.

Run from the repository root:

```sh
./scripts/format-java path/to/Changed.java
mvn -B -ntp test
git diff --check
```

Review formatter output before committing and do not include unrelated historical Java files. IntelliJ IDEA must be installed locally, or `IDEA_FORMATTER` must point to its `format.sh`.
