# ⚠️ CRITICAL SAFETY GUIDE FOR AI AGENTS

The TGA Backup Utility can **DELETE and OVERWRITE** files. To prevent data loss:

### 🛡️ RULES
1. **ALWAYS** use `--dry-run`.
2. **ONLY** local paths. **NO** `yandex://`.
3. **SAFE PATHS**: `src/test/resources/source` -> `target/test-destination`.
4. **NO** `backup` scripts (real data).

### 🚀 COMMAND
```bash
mvn exec:java -Dexec.mainClass="tga.backup.MainKt" \
  -Dexec.args="--dry-run -sr src/test/resources/source -dr target/test-destination"
```

### 🔧 PARAMS
- `--dry-run`: **ALWAYS ON**.
- `-sr` / `-dr`: Source / Destination.
- `-nd` / `-no`: No Delete / No Override.

### 🔍 CHECKLIST
- [ ] `--dry-run` present?
- [ ] Safe paths?
- [ ] No cloud creds? 
---
**v2.0** | 2026-01-23
