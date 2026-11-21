/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.all.screenrecording

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.findMutableMethodOf
import app.morphe.patcher.util.proxy.mutableTypes.MutableClassDef
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private val LISTENER_INTERFACES = setOf(
    "Landroid/hardware/display/DisplayManager\$DisplayListener;",
    "Landroid/view/Window\$ScreenRecordingCallback;"
)

private val CALLBACK_METHODS = setOf(
    "onDisplayAdded", "onDisplayRemoved", "onDisplayChanged", // DisplayListener
    "onStart", "onStop" // ScreenRecordingCallback
)

private val DETECTION_STRINGS = setOf(
    "screen_record", "screenrecord", "isrecording", "screen_mirror", "cast",
    "com.miui.screenrecorder", "com.samsung.app.screenrecorder", "com.google.android.apps.recorder"
)

@Suppress("unused")
val bypassScreenRecordingDetectionPatch = bytecodePatch(
    name = "Bypass screen recording detection",
    description = "Prevents apps from detecting screen recording, casting, or mirroring.",
    default = false,
) {
    execute {
        classDefForEach { classDef ->
            // 1. Neutralize framework callbacks
            // If the app implements these interfaces, they use them to detect virtual displays/recording.
            val implementsListener = classDef.interfaces.any { it in LISTENER_INTERFACES }
            if (implementsListener) {
                val mutableClass = mutableClassDefBy(classDef)
                for (method in mutableClass.methods) {
                    if (method.name in CALLBACK_METHODS && method.returnType == "V") {
                        method.addInstructions(0, "return-void")
                    }
                }
                return@classDefForEach
            }

            // 2. Neutralize app-specific boolean detectors (e.g., isScreenRecording()Z)
            for (method in classDef.methods) {
                if (method.returnType != "Z") continue
                val impl = method.implementation ?: continue

                val hasDetectionString = impl.instructions.any { ins ->
                    ins.opcode == Opcode.CONST_STRING &&
                    (ins.reference as? StringReference)?.string?.lowercase()?.let { str ->
                        DETECTION_STRINGS.any { it in str }
                    } == true
                }

                if (hasDetectionString) {
                    val mutableClass = mutableClassDefBy(classDef)
                    val mutableMethod = mutableClass.findMutableMethodOf(method)

                    // Use 'p0' for instance methods to avoid .locals allocation crashes.
                    // Fallback to 'v0' for static methods.
                    val reg = if (AccessFlags.STATIC.isSet(method.accessFlags)) "v0" else "p0"

                    mutableMethod.addInstructions(0, """
                        const/4 $reg, 0x0
                        return $reg
                    """.trimIndent())
                }
            }
        }
    }
}