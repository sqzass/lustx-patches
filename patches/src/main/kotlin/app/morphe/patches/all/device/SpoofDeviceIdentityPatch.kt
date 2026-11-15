package app.morphe.patches.all.device

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.mutableClassDefByOrNull
import app.morphe.patcher.patch.stringOption
import app.morphe.patcher.util.findMutableMethodOf
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlin.random.Random

private fun argRegisters(ins: Instruction): List<Int>? = when (ins) {
    is FiveRegisterInstruction -> listOf(
        ins.registerC,
        ins.registerD,
        ins.registerE,
        ins.registerF,
        ins.registerG,
    ).take(ins.registerCount)
    is RegisterRangeInstruction -> (
        ins.startRegister until ins.startRegister + ins.registerCount
    ).toList()
    else -> null
}

private fun randomHex(len: Int): String =
    List(len) { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

private fun randomMac(): String =
    List(6) { "%02x".format(Random.nextInt(256)) }.joinToString(":")

private fun isAndroidIdArg(method: MutableMethod, invokeIndex: Int, reg: Int): Boolean {
    for (j in invokeIndex - 1 downTo 0) {
        val ins = method.getInstruction(j)
        val op = ins.opcode
        if (ins is OneRegisterInstruction && ins.registerA == reg) {
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                return ins.getReference<StringReference>()?.string == "android_id"
            }
            if (op == Opcode.SGET_OBJECT) {
                val f = ins.getReference<FieldReference>()
                return f?.definingClass == "Landroid/provider/Settings\$Secure;" &&
                    f.name == "ANDROID_ID"
            }
            return false
        }
        if (
            op == Opcode.GOTO ||
            op == Opcode.GOTO_16 ||
            op == Opcode.GOTO_32 ||
            op == Opcode.PACKED_SWITCH ||
            op == Opcode.SPARSE_SWITCH ||
            op.name.startsWith("if-") ||
            op.name.startsWith("return")
        ) return false
    }
    return false
}

private fun overrideBuildField(
    mc: MutableClass,
    fieldName: String,
    newSmali: String,
) {
    val mm = mc.methods
        .filterIsInstance<MutableMethod>()
        .firstOrNull { it.name == "<clinit>" }
        ?: return
    val sputIdx = mm.instructions.withIndex().firstOrNull { (_, ins) ->
        (ins.opcode == Opcode.SPUT_OBJECT || ins.opcode == Opcode.SPUT) &&
            ins.getReference<FieldReference>()?.name == fieldName
    }?.index ?: return
    val sput = mm.getInstruction(sputIdx) as OneRegisterInstruction
    val srcReg = sput.registerA
    for (j in sputIdx - 1 downTo 0) {
        val ins = mm.getInstruction(j)
        if (ins is OneRegisterInstruction && ins.registerA == srcReg) {
            mm.replaceInstruction(j, newSmali.replace("%REG", "v$srcReg"))
            return
        }
        val op = ins.opcode
        if (
            op == Opcode.GOTO ||
            op.name.startsWith("if-") ||
            op.name.startsWith("return")
        ) return
    }
}

private class SimPreset(
    val label: String,
    val iso: String,
    val mcc: String,
    val mnc: String,
    val carrier: String,
) {
    val operator: String get() = mcc + mnc
}

private val SIM_PRESETS = listOf(
    SimPreset("Off", "", "", "", ""),
    SimPreset("US - T-Mobile", "us", "310", "260", "T-Mobile"),
    SimPreset("US - AT&T", "us", "310", "410", "AT&T"),
    SimPreset("US - Verizon", "us", "310", "012", "Verizon"),
    SimPreset("UK - EE", "gb", "234", "30", "EE"),
    SimPreset("UK - Vodafone", "gb", "234", "15", "Vodafone"),
    SimPreset("UK - O2", "gb", "234", "10", "O2"),
    SimPreset("DE - Telekom", "de", "262", "01", "Telekom"),
    SimPreset("DE - Vodafone", "de", "262", "02", "Vodafone"),
    SimPreset("FR - Orange", "fr", "208", "01", "Orange"),
    SimPreset("FR - SFR", "fr", "208", "10", "SFR"),
    SimPreset("ES - Movistar", "es", "214", "07", "Movistar"),
    SimPreset("IT - TIM", "it", "222", "01", "TIM"),
    SimPreset("NL - KPN", "nl", "204", "08", "KPN"),
    SimPreset("PL - Orange", "pl", "260", "03", "Orange"),
    SimPreset("BR - Vivo", "br", "724", "06", "Vivo"),
    SimPreset("IN - Jio", "in", "405", "854", "Jio"),
    SimPreset("IN - Airtel", "in", "404", "10", "Airtel"),
    SimPreset("JP - docomo", "jp", "440", "10", "docomo"),
    SimPreset("KR - SK Telecom", "kr", "450", "05", "SK Telecom"),
    SimPreset("AU - Telstra", "au", "505", "01", "Telstra"),
    SimPreset("CA - Rogers", "ca", "302", "720", "Rogers"),
    SimPreset("SG - StarHub", "sg", "525", "05", "StarHub"),
    SimPreset("AE - etisalat", "ae", "424", "02", "etisalat"),
    SimPreset("TR - Turkcell", "tr", "286", "01", "Turkcell"),
)

private class DevicePreset(
    val label: String,
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val fingerprint: String,
    val tags: String,
)

private val DEVICE_PRESETS = listOf(
    DevicePreset("Off", "", "", "", "", "", "", ""),
    DevicePreset(
        "Pixel 8 Pro",
        "Google",
        "Google",
        "Pixel 8 Pro",
        "husky",
        "husky",
        "google/husky/husky:14/AP2A.240905.003/12025182:user/release-keys",
        "release-keys",
    ),
    DevicePreset(
        "Samsung Galaxy S24 Ultra",
        "samsung",
        "samsung",
        "SM-S928B",
        "e3q",
        "e3qxxx",
        "samsung/e3qxxx/e3q:14/UP1A.231005.007/S928BXXU1AXL1:user/release-keys",
        "release-keys",
    ),
    DevicePreset(
        "Generic emulator (ranchu)",
        "generic",
        "Google",
        "sdk_gphone64_x86_64",
        "ranchu",
        "sdk_gphone64_x86_64",
        "google/sdk_gphone64_x86_64/ranchu:14/UE1A.230829.050/10811458:userdebug/dev-keys",
        "dev-keys",
    ),
    DevicePreset(
        "Genymotion",
        "Genymotion",
        "Genymotion",
        "GS101T",
        "vbox86p",
        "vbox86p",
        "genymotion/vbox86p/vbox86p:8.0.0/GPD28P/1234:userdebug/test-keys",
        "test-keys",
    ),
    DevicePreset(
        "BlueStacks",
        "BlueStacks",
        "BlueStacks",
        "HFX5W",
        "HFX5W",
        "HFX5W",
        "BlueStacks/HFX5W/HFX5W:9/PI2.181005.003/eng.bstack.20190101.123456:userdebug/test-keys",
        "test-keys",
    ),
    DevicePreset(
        "Nox Player",
        "Nox",
        "Nox",
        "VM",
        "VM",
        "VM",
        "Nox/VM/VM:9/PI2.181005.003/eng.nox.20190101.123456:userdebug/test-keys",
        "test-keys",
    ),
)

private fun spoofAndroidId(fakeId: String) {
    val targets = listOf(
        "Landroid/provider/Settings\$Secure;->getString(" +
            "Landroid/content/ContentResolver;Ljava/lang/String;" +
            ")Ljava/lang/String;",
        "Landroid/provider/Settings\$Secure;->getStringForUser(" +
            "Landroid/content/ContentResolver;Ljava/lang/String;I" +
            ")Ljava/lang/String;",
    )
    targets.forEach { smali ->
        methodCall(smali).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val regs = argRegisters(getInstruction(index)) ?: return@matchAllMethodIndicesForEach
            if (regs.size < 2) return@matchAllMethodIndicesForEach
            if (!isAndroidIdArg(this, index, regs[1])) return@matchAllMethodIndicesForEach
            val ri = index + 1
            if (ri >= instructions.size) return@matchAllMethodIndicesForEach
            val res = getInstruction(ri)
            if (res.opcode != Opcode.MOVE_RESULT_OBJECT) return@matchAllMethodIndicesForEach
            val reg = (res as OneRegisterInstruction).registerA
            replaceInstruction(ri, "const-string v$reg, \"$fakeId\"")
        }
    }
}

private fun spoofMac(fakeMac: String) {
    val targets = listOf(
        "Landroid/net/wifi/WifiInfo;->getMacAddress()Ljava/lang/String;",
        "Landroid/bluetooth/BluetoothAdapter;->getAddress()Ljava/lang/String;",
    )
    targets.forEach { smali ->
        methodCall(smali).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val ri = index + 1
            if (ri >= instructions.size) return@matchAllMethodIndicesForEach
            val res = getInstruction(ri)
            if (res.opcode != Opcode.MOVE_RESULT_OBJECT) return@matchAllMethodIndicesForEach
            val reg = (res as OneRegisterInstruction).registerA
            replaceInstruction(ri, "const-string v$reg, \"$fakeMac\"")
        }
    }
}

private fun spoofSim(p: SimPreset) {
    val getters = mapOf(
        "Landroid/telephony/TelephonyManager;->getNetworkCountryIso()" +
            "Ljava/lang/String;" to p.iso,
        "Landroid/telephony/TelephonyManager;->getSimCountryIso()" +
            "Ljava/lang/String;" to p.iso,
        "Landroid/telephony/TelephonyManager;->getNetworkOperator()" +
            "Ljava/lang/String;" to p.operator,
        "Landroid/telephony/TelephonyManager;->getSimOperator()" +
            "Ljava/lang/String;" to p.operator,
        "Landroid/telephony/TelephonyManager;->getSimOperatorName()" +
            "Ljava/lang/String;" to p.carrier,
        "Landroid/telephony/TelephonyManager;->getNetworkOperatorName()" +
            "Ljava/lang/String;" to p.carrier,
        "Landroid/telephony/SubscriptionInfo;->getCountryIso()" +
            "Ljava/lang/String;" to p.iso,
        "Landroid/telephony/SubscriptionInfo;->getCarrierName()" +
            "Ljava/lang/CharSequence;" to p.carrier,
        "Landroid/telephony/SubscriptionInfo;->getDisplayName()" +
            "Ljava/lang/CharSequence;" to p.carrier,
        "Landroid/telephony/SubscriptionInfo;->getMccString()" +
            "Ljava/lang/String;" to p.mcc,
        "Landroid/telephony/SubscriptionInfo;->getMncString()" +
            "Ljava/lang/String;" to p.mnc,
    )
    getters.forEach { (smali, value) ->
        methodCall(smali).matchAllMethodIndicesForEach(requireMatches = false) { index ->
            val ri = index + 1
            if (ri >= instructions.size) return@matchAllMethodIndicesForEach
            val res = getInstruction(ri)
            if (res.opcode != Opcode.MOVE_RESULT_OBJECT) return@matchAllMethodIndicesForEach
            val reg = (res as OneRegisterInstruction).registerA
            replaceInstruction(ri, "const-string v$reg, \"$value\"")
        }
    }
}

private fun spoofDevice(p: DevicePreset) {
    val mc = mutableClassDefByOrNull("Landroid/os/Build;") ?: return
    val fields = listOf(
        "BRAND" to p.brand,
        "MANUFACTURER" to p.manufacturer,
        "MODEL" to p.model,
        "DEVICE" to p.device,
        "PRODUCT" to p.product,
        "FINGERPRINT" to p.fingerprint,
        "TAGS" to p.tags,
    )
    fields.forEach { (name, value) ->
        if (value.isNotEmpty()) {
            overrideBuildField(mc, name, "const-string %REG, \"$value\"")
        }
    }
}

@Suppress("unused")
val spoofDeviceIdentityPatch = bytecodePatch(
    name = "Spoof device identity",
    description = "Unified device spoofing with per-category toggles: " +
        "Android ID, MAC address, SIM/carrier, and Build device fields " +
        "(including emulator presets). Replaces the former individual " +
        "SpoofAndroidId, SpoofSim, and SpoofDevice patches.",
    default = false,
) {
    val enableAndroidId by booleanOption(
        key = "enableAndroidId", default = false,
        title = "Spoof Android ID",
        description = "Overrides Settings.Secure 'android_id' reads.",
    )
    val androidIdValue = stringOption(
        key = "androidIdValue", default = "Random",
        values = mapOf("Random" to "Random"),
        title = "Android ID value",
        description = "16 hex chars, or 'Random' to generate one per build.",
        required = false,
    ) { it == "Random" || (it != null && it.matches(Regex("^[0-9a-f]{16}$"))) }

    val enableMac by booleanOption(
        key = "enableMac", default = false,
        title = "Spoof MAC address",
        description = "Overrides WifiInfo.getMacAddress() and " +
            "BluetoothAdapter.getAddress().",
    )
    val macValue = stringOption(
        key = "macValue", default = "Random",
        values = mapOf("Random" to "Random"),
        title = "MAC address value",
        description = "Format AA:BB:CC:DD:EE:FF, or 'Random'.",
        required = false,
    ) { it == "Random" || (it != null && it.matches(Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$"))) }

    val enableSim by booleanOption(
        key = "enableSim", default = false,
        title = "Spoof SIM / carrier",
        description = "Overrides TelephonyManager and SubscriptionInfo " +
            "country/operator getters.",
    )
    val simPreset = stringOption(
        key = "simPreset", default = SIM_PRESETS[1].label,
        values = SIM_PRESETS.associate { it.label to it.label },
        title = "SIM preset",
        description = "The simulated SIM/network identity.",
        required = false,
    )

    val enableDevice by booleanOption(
        key = "enableDevice", default = false,
        title = "Spoof device / Build fields",
        description = "Overrides Build.BRAND/MANUFACTURER/MODEL/DEVICE/" +
            "PRODUCT/FINGERPRINT/TAGS. Includes emulator presets.",
    )
    val devicePreset = stringOption(
        key = "devicePreset", default = DEVICE_PRESETS[1].label,
        values = DEVICE_PRESETS.associate { it.label to it.label },
        title = "Device preset",
        description = "Target device identity. Emulator presets use " +
            "test-keys/dev-keys tags.",
        required = false,
    )

    execute {
        if (enableAndroidId == true) {
            val id = if (androidIdValue.value == "Random") randomHex(16) else androidIdValue.value!!
            spoofAndroidId(id)
        }

        if (enableMac == true) {
            val mac = if (macValue.value == "Random") randomMac() else macValue.value!!
            spoofMac(mac)
        }

        if (enableSim == true) {
            val p = SIM_PRESETS.firstOrNull { it.label == simPreset.value }
            if (p != null && p.iso.isNotEmpty()) {
                spoofSim(p)
            }
        }

        if (enableDevice == true) {
            val p = DEVICE_PRESETS.firstOrNull { it.label == devicePreset.value }
            if (p != null && p.brand.isNotEmpty()) {
                spoofDevice(p)
            }
        }
    }
}