package tga.backup.files

/**
 * Optimized exclusion pattern matcher that categorizes patterns into different types
 * for performance optimization when checking large numbers of files.
 *
 * Pattern types:
 * 1. Full match - exact string comparison (e.g., ".md5", "Thumbs.db")
 * 2. Prefix - string ends with "*" (e.g., "._*", ".~lock*")
 * 3. Suffix - string starts with "*" (e.g., "*.tmp", "*.bak")
 * 4. Substring - string starts and ends with "*" (e.g., "*cache*", "*temp*")
 * 5. Regex - prefixed with "regex:" (e.g., "regex:^file[0-9]+\\.txt$")
 *
 * Patterns containing "/" are matched against full file paths (folder + file name).
 * Patterns without "/" are matched against file names only.
 */
class ExclusionMatcher(patterns: List<String>) {
    
    private val fullMatches: Set<String>
    private val prefixes: List<String>
    private val suffixes: List<String>
    private val substrings: List<String>
    private val regexes: List<Regex>
    
    private val pathFullMatches: Set<String>
    private val pathPrefixes: List<String>
    private val pathSuffixes: List<String>
    private val pathSubstrings: List<String>
    private val pathRegexes: List<Regex>
    
    init {
        val fullMatchList = mutableListOf<String>()
        val prefixList = mutableListOf<String>()
        val suffixList = mutableListOf<String>()
        val substringList = mutableListOf<String>()
        val regexList = mutableListOf<Regex>()
        
        val pathFullMatchList = mutableListOf<String>()
        val pathPrefixList = mutableListOf<String>()
        val pathSuffixList = mutableListOf<String>()
        val pathSubstringList = mutableListOf<String>()
        val pathRegexList = mutableListOf<Regex>()
        
        for (pattern in patterns) {
            val isPathPattern = pattern.contains("/")
            val targetFullMatchList = if (isPathPattern) pathFullMatchList else fullMatchList
            val targetPrefixList = if (isPathPattern) pathPrefixList else prefixList
            val targetSuffixList = if (isPathPattern) pathSuffixList else suffixList
            val targetSubstringList = if (isPathPattern) pathSubstringList else substringList
            val targetRegexList = if (isPathPattern) pathRegexList else regexList
            
            when {
                pattern.startsWith("regex:") -> {
                    // Regex pattern
                    val regexPattern = pattern.substring(6) // Remove "regex:" prefix
                    targetRegexList.add(Regex(regexPattern))
                }
                pattern.startsWith("*") && pattern.endsWith("*") && pattern.length > 2 -> {
                    // Substring pattern (e.g., "*cache*", "*temp*")
                    targetSubstringList.add(pattern.substring(1, pattern.length - 1))
                }
                pattern.endsWith("*") && !pattern.startsWith("*") -> {
                    // Prefix pattern (e.g., "._*")
                    targetPrefixList.add(pattern.substring(0, pattern.length - 1))
                }
                pattern.startsWith("*") && !pattern.endsWith("*") -> {
                    // Suffix pattern (e.g., "*.tmp")
                    targetSuffixList.add(pattern.substring(1))
                }
                pattern == "*" -> {
                    // Special case: match everything - treat as regex
                    targetRegexList.add(Regex(".*"))
                }
                pattern.contains("*") -> {
                    // Complex wildcard pattern - treat as regex
                    // Convert simple wildcards to regex
                    val regexPattern = pattern.replace(".", "\\.").replace("*", ".*")
                    targetRegexList.add(Regex(regexPattern))
                }
                else -> {
                    // Full match pattern (e.g., ".md5", "Thumbs.db")
                    targetFullMatchList.add(pattern)
                }
            }
        }
        
        fullMatches = fullMatchList.toSet()
        prefixes = prefixList
        suffixes = suffixList
        substrings = substringList
        regexes = regexList
        
        pathFullMatches = pathFullMatchList.toSet()
        pathPrefixes = pathPrefixList
        pathSuffixes = pathSuffixList
        pathSubstrings = pathSubstringList
        pathRegexes = pathRegexList
    }
    
    /**
     * Check if the given filename or path matches any exclusion pattern.
     * Uses optimized matching: full match (O(1)), prefix/suffix/substring (O(n)), regex (O(n*m))
     * 
     * @param fileName The file name (without path) to check
     * @param fullPath The full path (folder + file name) to check against path patterns. Optional.
     */
    fun isExcluded(fileName: String, fullPath: String? = null): Boolean {
        // Check file name patterns
        if (matchesPatterns(fileName, fullMatches, prefixes, suffixes, substrings, regexes)) {
            return true
        }
        
        // Check path patterns if full path is provided
        if (fullPath != null && matchesPatterns(fullPath, pathFullMatches, pathPrefixes, pathSuffixes, pathSubstrings, pathRegexes)) {
            return true
        }
        
        return false
    }
    
    private fun matchesPatterns(
        text: String,
        fullMatches: Set<String>,
        prefixes: List<String>,
        suffixes: List<String>,
        substrings: List<String>,
        regexes: List<Regex>
    ): Boolean {
        // 1. Fast full match check (O(1) with HashSet)
        if (text in fullMatches) return true
        
        // 2. Prefix check (O(n) where n is number of prefixes)
        for (prefix in prefixes) {
            if (text.startsWith(prefix)) return true
        }
        
        // 3. Suffix check (O(n) where n is number of suffixes)
        for (suffix in suffixes) {
            if (text.endsWith(suffix)) return true
        }
        
        // 4. Substring check (O(n) where n is number of substrings)
        for (substring in substrings) {
            if (text.contains(substring)) return true
        }
        
        // 5. Regex check (slowest, only if needed)
        for (regex in regexes) {
            if (regex.matches(text)) return true
        }
        
        return false
    }
}
