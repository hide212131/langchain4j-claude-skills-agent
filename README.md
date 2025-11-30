# langchain4j-claude-skills-agent

Traceability process materials (TDL docs, templates, and supporting scripts) in this branch are adapted from the Kopi project (https://github.com/kopi-vm/kopi) and remain under the Apache License 2.0. We do not claim copyright over those copied portions; original rights stay with the Kopi authors.

## 依存インストールとドキュメント整形

- `bun install` で依存を取得してください。npm公式レジストリが 403 を返す環境に備え、`bunfig.toml` で `https://registry.npmmirror.com` をデフォルトに設定しています。
- インストール後、Markdown の整形は `bun format`、Lint は `bun lint` を実行してください。
- レジストリにアクセスできず失敗した場合は、プロキシ設定やネットワーク制限を確認した上で再試行してください。
