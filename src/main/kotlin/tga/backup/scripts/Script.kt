package tga.backup.scripts

import tga.backup.params.Params

abstract class Script(val params: Params) {
    abstract fun run()
}
