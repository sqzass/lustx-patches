/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.all.screenshot

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.bytecodePatchClass
import app.morphe.patcher.patch.findMutableClassOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val bypassScreenshotDetectionPatch = bytecodePatch(
    name = "Bypass screenshot detection",
    description = "Prevents apps from detecting screenshots and screen recordings.",
    default = false,
) {
    execute {
        // 1. Neutralize ContentObserver registration for media URIs
        // Apps register observers on MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        // to detect new screenshot files
        bytecodePatchClass { classDef ->
            val mutableClass = classDef.findMutableClassOf(classDef)
            for (method in mutableClass.methods) {
                val impl = method.implementation ?: continue
                val instructions = impl.instructions.toList()
                
                for ((index, instruction) in instructions.withIndex()) {
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_INTERFACE
                    ) continue
                    
                    val ref = instruction.reference as? MethodReference ?: continue
                    if (ref.definingClass != "Landroid/content/ContentResolver;" ||
                        ref.name != "registerContentObserver"
                    ) continue
                    
                    // Check if this is observing a media URI (heuristic: check nearby const-string)
                    val nearbyStrings = instructions
                        .subList(maxOf(0, index - 10), minOf(instructions.size, index + 1))
                        .filter { it.opcode == Opcode.CONST_STRING }
                        .mapNotNull { (it.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)?.string }
                    
                    val isMediaObserver = nearbyStrings.any { str ->
                        str.contains("media", ignoreCase = true) ||
                        str.contains("images", ignoreCase = true) ||
                        str.contains("pictures", ignoreCase = true) ||
                        str.contains("screenshot", ignoreCase = true)
                    }
                    
                    if (isMediaObserver) {
                        // Replace the registerContentObserver call with nothing (skip it)
                        method.addInstructions(index, "return-void")
                        break
                    }
                }
            }
        }
        
        // 2. Neutralize FileObserver for screenshot directories
        bytecodePatchClass { classDef ->
            val mutableClass = classDef.findMutableClassOf(classDef)
            for (method in mutableClass.methods) {
                val impl = method.implementation ?: continue
                val instructions = impl.instructions.toList()
                
                for ((index, instruction) in instructions.withIndex()) {
                    if (instruction.opcode != Opcode.NEW_INSTANCE) continue
                    
                    val ref = instruction.reference as? com.android.tools.smali.dexlib2.iface.reference.TypeReference ?: continue
                    if (ref.type != "Landroid/os/FileObserver;") continue
                    
                    // Check if this FileObserver watches screenshot-related paths
                    val nearbyStrings = instructions
                        .subList(maxOf(0, index - 10), minOf(instructions.size, index + 1))
                        .filter { it.opcode == Opcode.CONST_STRING }
                        .mapNotNull { (it.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)?.string }
                    
                    val isScreenshotPath = nearbyStrings.any { str ->
                        str.contains("Screenshots", ignoreCase = true) ||
                        str.contains("Pictures", ignoreCase = true) ||
                        str.contains("DCIM", ignoreCase = true)
                    }
                    
                    if (isScreenshotPath) {
                        // Neutralize the FileObserver constructor or startWatching() call
                        // Find the <init> call for this FileObserver
                        for (i in index until minOf(instructions.size, index + 5)) {
                            val initCall = instructions[i]
                            if (initCall.opcode == Opcode.INVOKE_DIRECT) {
                                val initRef = initCall.reference as? MethodReference ?: continue
                                if (initRef.definingClass == "Landroid/os/FileObserver;" &&
                                    initRef.name == "<init>"
                                ) {
                                    // Replace constructor call with no-op
                                    method.addInstructions(i, "// bypassed FileObserver")
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}