/*
 * Copyright 2026 sqzass.
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.alightmotion.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

/**
 * Compatibility lock for Lustx's Alight Motion suite.
 *
 * VERSION POLICY: locked to 5.0.270.1002578 (versionCode 1002578) ONLY. Every anchor
 * in this suite uses exact obfuscated descriptors from that build — Alight obfuscates
 * every release, so a version mismatch must fail loud (Compatibility) rather than patch
 * the wrong code. A new Alight version = fresh recon, never fuzzy reuse.
 *
 * Stock base: base.apk sha256 cafd733c6bbc12d17b8a43a5f779587c89b03cc8e64f234e6fc881b0f1c99e87
 */
val ALIGHT_5_0_270 = Compatibility(
    name = "Alight Motion",
    packageName = "com.alightcreative.motion",
    description = "Requires the original 5.0.270.1002578 APK. Anchors are exact obfuscated descriptors from that build only.",
    apkFileType = ApkFileType.APK,
    targets = listOf(
        AppTarget(version = "5.0.270.1002578", versionCode = 1002578),
    ),
)
