package tga.backup.files

/**
 * Optimized exclusion pattern matcher that categorizes patterns into different types
 * for performance optimization when checking large numbers of files.
 *
 * Pattern types:
 * 1. Full match - exact string comparison (e.g., ".md5", "Thumbs.db")
 * 2. Prefix - string ends with "*" (e.g., "._*", ".~lock*")
 * 3. Suffix - string starts with "*" (e.g., "*.tmp", "*.bak")
 * 4. Regex - prefixed with "regex:" (e.g., "regex:^file[0-9]+\\.txt$")
 */
class ExclusionMatcher(patterns: List<String>) {
    
    private val fullMatches: Set<String>
    private val prefixes: List<String>
    private val suffixes: List<String>
    private val regexes: List<Regex>
    
    init {
        val fullMatchList = mutableListOf<String>()
        val prefixList = mutableListOf<String>()
        val suffixList = mutableListOf<String>()
        val regexList = mutableListOf<Regex>()
        
        for (pattern in patterns) {
            when {
                pattern.startsWith("regex:") -> {
                    // Regex pattern
                    val regexPattern = pattern.substring(6) // Remove "regex:" prefix
                    regexList.add(Regex(regexPattern))
                }
                pattern.endsWith("*") && !pattern.startsWith("*") -> {
                    // Prefix pattern (e.g., "._*")
                    prefixList.add(pattern.substring(0, pattern.length - 1))
                }
                pattern.startsWith("*") && !pattern.endsWith("*") -> {
                    // Suffix pattern (e.g., "*.tmp")
                    suffixList.add(pattern.substring(1))
                }
                pattern == "*" -> {
                    // Special case: match everything - treat as regex
                    regexList.add(Regex(".*"))
                }
                pattern.contains("*") -> {
                    // Complex wildcard pattern - treat as regex
                    // Convert simple wildcards to regex
                    val regexPattern = pattern.replace(".", "\\.").replace("*", ".*")
                    regexList.add(Regex(regexPattern))
                }
                else -> {
                    // Full match pattern (e.g., ".md5", "Thumbs.db")
                    fullMatchList.add(pattern)
                }
            }
        }
        
        fullMatches = fullMatchList.toSet()
        prefixes = prefixList
        suffixes = suffixList
        regexes = regexList
    }
    
    /**
     * Check if the given filename matches any exclusion pattern.
     * Uses optimized matching: full match (O(1)), prefix/suffix (O(n)), regex (O(n*m))
     */
    fun isExcluded(fileName: String): Boolean {
        // 1. Fast full match check (O(1) with HashSet)
        if (fileName in fullMatches) return true
        
        // 2. Prefix check (O(n) where n is number of prefixes)
        for (prefix in prefixes) {
            if (fileName.startsWith(prefix)) return true
        }
        
        // 3. Suffix check (O(n) where n is number of suffixes)
        for (suffix in suffixes) {
            if (fileName.endsWith(suffix)) return true
        }
        
        // 4. Regex check (slowest, only if needed)
        for (regex in regexes) {
            if (regex.matches(fileName)) return true
        }
        
        return false
    }
}
