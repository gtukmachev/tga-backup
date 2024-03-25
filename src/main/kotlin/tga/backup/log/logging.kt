package tga.backup.log

fun <T> logWrap(prefix: String, eatErrors: Boolean = false, body: () -> T): T? {
    print(prefix)
    return try {
        body().also { print("...ok") }
    } catch (t: Throwable) {
        print("...${t.toLog()}")
        if (!eatErrors) throw t
        null
    } finally {
        println()
    }

}

fun Throwable.toLog() = "${this::class.java.simpleName}: '${this.message}'"

