package tga.backup.files

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExclusionMatcherTest {

    @Test
    fun `test full match patterns`() {
        val matcher = ExclusionMatcher(listOf(".md5", "Thumbs.db", "desktop.ini"))
        
        // Should match
        assertThat(matcher.isExcluded(".md5")).isTrue()
        assertThat(matcher.isExcluded("Thumbs.db")).isTrue()
        assertThat(matcher.isExcluded("desktop.ini")).isTrue()
        
        // Should not match
        assertThat(matcher.isExcluded(".md5.bak")).isFalse()
        assertThat(matcher.isExcluded("prefix.md5")).isFalse()
        assertThat(matcher.isExcluded("Thumbs.db.old")).isFalse()
    }

    @Test
    fun `test prefix patterns`() {
        val matcher = ExclusionMatcher(listOf("._*", ".~lock*", "temp_*"))
        
        assertThat(matcher.isExcluded("._file.txt")).isTrue()
        assertThat(matcher.isExcluded("._")).isTrue()
        assertThat(matcher.isExcluded(".~lock.file")).isTrue()
        assertThat(matcher.isExcluded(".~lockfile")).isTrue()
        
        assertThat(matcher.isExcluded("file._")).isFalse()
        assertThat(matcher.isExcluded("lock.txt")).isFalse()
    }

    @Test
    fun `test suffix patterns`() {
        val matcher = ExclusionMatcher(listOf("*.tmp", "*.bak", "*.log"))
        
        assertThat(matcher.isExcluded("file.tmp")).isTrue()
        assertThat(matcher.isExcluded("data.bak")).isTrue()
        assertThat(matcher.isExcluded("test.log")).isTrue()
        assertThat(matcher.isExcluded("file.txt")).isFalse()
    }
    
    @Test
    fun `test regex patterns`() {
        val matcher = ExclusionMatcher(listOf(
            "regex:^file[0-9]+\\.txt$",
            "regex:^test_.*\\.log$"
        ))
        
        assertThat(matcher.isExcluded("file123.txt")).isTrue()
        assertThat(matcher.isExcluded("file.txt")).isFalse()
        assertThat(matcher.isExcluded("test123.log")).isFalse()
    }
    
    @Test
    fun `test mixed pattern types`() {
        val matcher = ExclusionMatcher(listOf(
            ".md5",           // full match
            "._*",            // prefix
            "*.tmp",          // suffix
            "regex:^file[0-9]+\\.txt$"  // regex
        ))
        
        // Full match
        assertThat(matcher.isExcluded(".md5")).isTrue()
        assertThat(matcher.isExcluded(".md5.backup")).isFalse()
        
        // Prefix
        assertThat(matcher.isExcluded("._something")).isTrue()
        assertThat(matcher.isExcluded("._")).isTrue()
        assertThat(matcher.isExcluded("other._file")).isFalse()
        
        // Suffix
        assertThat(matcher.isExcluded("file.tmp")).isTrue()
        assertThat(matcher.isExcluded(".tmp")).isTrue()
        
        // Regex
        assertThat(matcher.isExcluded("file123.txt")).isTrue()
        assertThat(matcher.isExcluded("file.txt")).isFalse()
    }
    
    @Test
    fun `test comprehensive mixed patterns`() {
        val patterns = listOf(
            ".md5",           // full match
            "._*",            // prefix
            "*.tmp",          // suffix
            "regex:^test[0-9]+\\.txt$"  // regex
        )
        val matcher = ExclusionMatcher(patterns)
        
        // Full match
        assertThat(matcher.isExcluded(".md5")).isTrue()
        assertThat(matcher.isExcluded(".md5.bak")).isFalse()
        
        // Prefix
        assertThat(matcher.isExcluded("._file")).isTrue()
        assertThat(matcher.isExcluded("._")).isTrue()
        assertThat(matcher.isExcluded("file._")).isFalse()
        
        // Suffix
        assertThat(matcher.isExcluded("file.tmp")).isTrue()
        assertThat(matcher.isExcluded(".tmp")).isTrue()
        assertThat(matcher.isExcluded("tmp")).isFalse()
        
        // Regex
        assertThat(matcher.isExcluded("test123.txt")).isTrue()
        assertThat(matcher.isExcluded("test.txt")).isFalse()
        assertThat(matcher.isExcluded("testabc.txt")).isFalse()
    }

    @Test
    fun `test empty patterns`() {
        val matcher = ExclusionMatcher(emptyList())
        
        assertThat(matcher.isExcluded("anyfile.txt")).isFalse()
        assertThat(matcher.isExcluded(".md5")).isFalse()
    }

    @Test
    fun `test real world patterns from application conf`() {
        val patterns = listOf(
            ".md5",
            "._*",
            "Thumbs.db",
            "ZbThumbnail.info",
            "desktop.ini",
            ".tmp.driveupload",
            ".~lock*",
            "picasa.ini",
            ".picasa.ini"
        )
        val matcher = ExclusionMatcher(patterns)
        
        // Full matches
        assertThat(matcher.isExcluded(".md5")).isTrue()
        assertThat(matcher.isExcluded("Thumbs.db")).isTrue()
        assertThat(matcher.isExcluded("ZbThumbnail.info")).isTrue()
        assertThat(matcher.isExcluded("desktop.ini")).isTrue()
        assertThat(matcher.isExcluded(".tmp.driveupload")).isTrue()
        assertThat(matcher.isExcluded("picasa.ini")).isTrue()
        assertThat(matcher.isExcluded(".picasa.ini")).isTrue()
        
        // Prefixes
        assertThat(matcher.isExcluded("._file.txt")).isTrue()
        assertThat(matcher.isExcluded("._DS_Store")).isTrue()
        assertThat(matcher.isExcluded(".~lock.file.odt")).isTrue()
        assertThat(matcher.isExcluded(".~lockfile")).isTrue()
        
        // Should not match
        assertThat(matcher.isExcluded("md5")).isFalse()
        assertThat(matcher.isExcluded("file.md5")).isFalse()
        assertThat(matcher.isExcluded("thumbs.db")).isFalse() // case sensitive
        assertThat(matcher.isExcluded("myfile.txt")).isFalse()
    }

    @Test
    fun `test substring patterns`() {
        val matcher = ExclusionMatcher(listOf("*cache*", "*temp*", "*backup*"))
        
        // Should match
        assertThat(matcher.isExcluded("mycache")).isTrue()
        assertThat(matcher.isExcluded("cache.txt")).isTrue()
        assertThat(matcher.isExcluded("file_cache_data")).isTrue()
        assertThat(matcher.isExcluded("temporary")).isTrue()
        assertThat(matcher.isExcluded("temp")).isTrue()
        assertThat(matcher.isExcluded("backup_file")).isTrue()
        assertThat(matcher.isExcluded("file.backup.txt")).isTrue()
        
        // Should not match
        assertThat(matcher.isExcluded("file.txt")).isFalse()
        assertThat(matcher.isExcluded("data.log")).isFalse()
    }

    @Test
    fun `test folder path exclusions - full match`() {
        val matcher = ExclusionMatcher(listOf("node_modules/package.json", ".git/config"))
        
        // Should match when full path is provided
        assertThat(matcher.isExcluded("package.json", "node_modules/package.json")).isTrue()
        assertThat(matcher.isExcluded("config", ".git/config")).isTrue()
        
        // Should not match file name only
        assertThat(matcher.isExcluded("package.json")).isFalse()
        assertThat(matcher.isExcluded("config")).isFalse()
        
        // Should not match different paths
        assertThat(matcher.isExcluded("package.json", "src/package.json")).isFalse()
    }

    @Test
    fun `test folder path exclusions - prefix`() {
        val matcher = ExclusionMatcher(listOf("node_modules/*", ".git/*", "build/temp/*"))
        
        // Should match when full path is provided
        assertThat(matcher.isExcluded("file.txt", "node_modules/file.txt")).isTrue()
        assertThat(matcher.isExcluded("index", ".git/index")).isTrue()
        assertThat(matcher.isExcluded("data.tmp", "build/temp/data.tmp")).isTrue()
        
        // Should not match file name only
        assertThat(matcher.isExcluded("file.txt")).isFalse()
        
        // Should not match different paths
        assertThat(matcher.isExcluded("file.txt", "src/file.txt")).isFalse()
    }

    @Test
    fun `test folder path exclusions - suffix`() {
        val matcher = ExclusionMatcher(listOf("*/cache", "*/temp"))
        
        // Should match when full path ends with pattern
        assertThat(matcher.isExcluded("cache", "build/cache")).isTrue()
        assertThat(matcher.isExcluded("temp", "data/temp")).isTrue()
        assertThat(matcher.isExcluded("cache", "node_modules/lib/cache")).isTrue()
        
        // Should not match file name only
        assertThat(matcher.isExcluded("cache")).isFalse()
        
        // Should not match different paths
        assertThat(matcher.isExcluded("cache", "cache/file.txt")).isFalse()
    }

    @Test
    fun `test folder path exclusions - substring`() {
        val matcher = ExclusionMatcher(listOf("*node_modules/*", "*/.git/*"))
        
        // Should match when full path contains pattern
        assertThat(matcher.isExcluded("file.txt", "project/node_modules/file.txt")).isTrue()
        assertThat(matcher.isExcluded("file.txt", "node_modules/lib/file.txt")).isTrue()
        assertThat(matcher.isExcluded("index", "project/.git/index")).isTrue()
        
        // Should not match file name only
        assertThat(matcher.isExcluded("file.txt")).isFalse()
        
        // Should not match different paths
        assertThat(matcher.isExcluded("file.txt", "src/file.txt")).isFalse()
    }

    @Test
    fun `test mixed file and folder patterns`() {
        val matcher = ExclusionMatcher(listOf(
            ".md5",              // file full match
            "*.tmp",             // file suffix
            "*cache*",           // file substring
            "node_modules/*",    // folder prefix
            "*/build",           // folder suffix
            "*/.git/*"           // folder substring
        ))
        
        // File patterns
        assertThat(matcher.isExcluded(".md5")).isTrue()
        assertThat(matcher.isExcluded("file.tmp")).isTrue()
        assertThat(matcher.isExcluded("mycache.txt")).isTrue()
        
        // Folder patterns
        assertThat(matcher.isExcluded("package.json", "node_modules/package.json")).isTrue()
        assertThat(matcher.isExcluded("build", "project/build")).isTrue()
        assertThat(matcher.isExcluded("config", "project/.git/config")).isTrue()
        
        // Should not match
        assertThat(matcher.isExcluded("file.txt")).isFalse()
        assertThat(matcher.isExcluded("file.txt", "src/file.txt")).isFalse()
    }
}
