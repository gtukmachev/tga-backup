package tga.backup.utils

import tga.backup.terminal.Terminal

fun main() {
    Terminal.setupUtf8Console()

    println("Привет, мир! ✅ Юникод работает корректно.")
    println("Проверка символов: ☑ ☐ ⚠ ✔ ✘ → ←")
}
