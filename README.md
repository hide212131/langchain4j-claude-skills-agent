# langchain4j-claude-skills-agent

Traceability process materials (TDL docs, templates, and supporting scripts) in this branch are adapted from the Kopi project (https://github.com/kopi-vm/kopi) and remain under the Apache License 2.0. We do not claim copyright over those copied portions; original rights stay with the Kopi authors.

## 実行方法

Picocli ベースの最小 CLI を Gradle から起動できます。

```bash
./gradlew run --args="--skill app/src/test/resources/skills/e2e/SKILL.md --goal \"デモゴール\" --visibility-level basic"
```

- `--skill` : 実行する SKILL.md のパス（必須）
- `--goal` : エージェントに与えるゴール（任意）
- `--visibility-level` : 可視化ログレベル。`basic`（デフォルト）または `off`

正常終了時は Plan/Act/Reflect のログと成果物が標準出力に表示されます。
