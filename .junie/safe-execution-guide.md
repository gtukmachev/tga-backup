# CRITICAL SAFETY GUIDE FOR AI AGENTS
Use the rules bellow to avoid any manypulatings of real files (any data loss / modifications) during test / build runs,

### 🛡️ RULES
1. To eun program in terminal (don't run command line scripts) - use `mvn exec:java -Dexec.mainClass=...` syntax.
2. **ALWAYS** use `--dry-run` parameter.
3. **SAFE PATHS**:
   Try to use safe paths (that we prepared in our project) via parameters
   `-sr src/test/resources/source`
   `-dr target/test-destination`
4. Try to avoid `yandex://` or other remote paths in source or destination.
   1. **Only** if it required for a certain test - you may use it, but use it together with remote cache `-rm`, and generatet corresponded local chache file for the case (to simulate remote file system state and response effect).
   2. **Always** add remote credential parameters with empty value when use remote file systems (to override real values from ENV variables or run-profiles): `-yu "" -yt ""`.

### PARAMS helper
- `--dry-run`: **ALWAYS ON**.
- `-sr` / `-dr`: Source / Destination.
- `-rm` - remote cache activating (requires cash files under user home root ~/.tga-bacjup/<profile>/....)
- `-yu` & `-yt` - remote credentials parameters
