package app.morphe.patches.all.security

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.util.findMutableMethodOf
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method

private val ROOT_RE = Regex(
    "^(is|has|check|detect|verify|test|find|look|scan)" +
        ".*(root|su|magisk|kernelsu|supersu|superuser)" +
        ".*(ed|ing|s)?$",
    RegexOption.IGNORE_CASE,
)

private val EMU_RE = Regex(
    "^(is|check|detect|verify|test|find|look|scan)" +
        ".*(emulator|qemu|genymotion|bluestacks|nox|mumu|virtualbox|" +
        "vmware|hyper-v|androidstudio|ldplayer|memu)" +
        ".*(ed|ing|s)?$",
    RegexOption.IGNORE_CASE,
)

private fun shouldNoOp(m: Method): Boolean {
    val name = m.name
    val ret = m.returnType
    if (ret == "Z") return ROOT_RE.matches(name) || EMU_RE.matches(name)
    if (ret == "V") return ROOT_RE.matches(name) || EMU_RE.matches(name)
    return false
}

private fun processClass(classDef: ClassDef) {
    val targets = classDef.methods.filter(::shouldNoOp)
    if (targets.isEmpty()) return
    val mc = mutableClassDefBy(classDef)
    targets.forEach { m ->
        val mm = mc.findMutableMethodOf(m)
        when (m.returnType) {
            "Z" -> mm.returnEarly(false)
            "V" -> mm.returnEarly()
        }
    }
}

@Suppress("unused")
val bypassRootEmulatorPatch = bytecodePatch(
    name = "Bypass root / emulator detection",
    description = "Forces common root and emulator detection methods to report clean. " +
        "Covers Java/Kotlin-level checks; native (.so) detection is not affected.",
    default = false,
) {
    execute {
        classDefForEach(::processClass)
    }
}