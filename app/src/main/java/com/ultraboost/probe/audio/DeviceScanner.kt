package com.ultraboost.probe.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import java.util.UUID

enum class ProbeEffect(
    val title: String,
    val detail: String,
    val type: UUID
) {
    LOUDNESS(
        "Loudness Enhancer",
        "Test oturumunun algılanan ses seviyesini artırmayı dener.",
        AudioEffect.EFFECT_TYPE_LOUDNESS_ENHANCER
    ),
    BASS(
        "Bass Boost",
        "Test oturumundaki düşük frekansları güçlendirmeyi dener.",
        AudioEffect.EFFECT_TYPE_BASS_BOOST
    ),
    EQUALIZER(
        "Equalizer",
        "Test oturumunda frekans denetiminin açılabildiğini kontrol eder.",
        AudioEffect.EFFECT_TYPE_EQUALIZER
    ),
    VIRTUALIZER(
        "Virtualizer",
        "Test oturumunda sanal uzamsal ses efektini dener.",
        AudioEffect.EFFECT_TYPE_VIRTUALIZER
    )
}

data class EffectInfo(
    val effect: ProbeEffect,
    val available: Boolean,
    val implementation: String
)

data class OutputInfo(
    val name: String,
    val type: String
)

data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val effects: List<EffectInfo>,
    val outputs: List<OutputInfo>,
    val scanNote: String? = null
) {
    fun toShareText(): String = buildString {
        appendLine("UltraBoost Probe — Cihaz raporu")
        appendLine("Cihaz: $manufacturer $model")
        appendLine("Android: $androidVersion (API $apiLevel)")
        appendLine()
        appendLine("Kayıtlı ses efektleri:")
        effects.forEach { info ->
            appendLine("- ${info.effect.title}: ${if (info.available) "Bulundu" else "Bulunamadı"}")
            if (info.available && info.implementation.isNotBlank()) {
                appendLine("  Uygulayıcı: ${info.implementation}")
            }
        }
        appendLine()
        appendLine("Ses çıkışları:")
        if (outputs.isEmpty()) appendLine("- Liste alınamadı veya çıkış bulunamadı")
        outputs.forEach { appendLine("- ${it.name} (${it.type})") }
        appendLine()
        appendLine("Not: Efektin listede bulunması, YouTube/oyun gibi diğer uygulamaların sesine sistem genelinde uygulanabildiğini kanıtlamaz.")
        scanNote?.let { appendLine("Tarama notu: $it") }
    }
}

object DeviceScanner {
    fun scan(context: Context): DeviceReport {
        var note: String? = null
        val descriptors = try {
            AudioEffect.queryEffects()?.toList().orEmpty()
        } catch (error: Exception) {
            note = "Ses efektleri listelenirken hata oluştu: ${error.javaClass.simpleName}"
            emptyList()
        }

        val effectInfo = ProbeEffect.entries.map { effect ->
            val matching = descriptors.firstOrNull { descriptor -> descriptor.type == effect.type }
            EffectInfo(
                effect = effect,
                available = matching != null,
                implementation = matching?.implementor.orEmpty()
            )
        }

        val outputs = try {
            val audioManager = context.getSystemService(AudioManager::class.java)
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .map { device ->
                    OutputInfo(
                        name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Bilinmeyen cihaz",
                        type = describeDeviceType(device.type)
                    )
                }
                .distinctBy { "${it.name}|${it.type}" }
        } catch (error: Exception) {
            note = listOfNotNull(note, "Ses çıkışları okunamadı: ${error.javaClass.simpleName}")
                .joinToString("; ")
            emptyList()
        }

        return DeviceReport(
            manufacturer = Build.MANUFACTURER.orUnknown(),
            model = Build.MODEL.orUnknown(),
            androidVersion = Build.VERSION.RELEASE.orUnknown(),
            apiLevel = Build.VERSION.SDK_INT,
            effects = effectInfo,
            outputs = outputs,
            scanNote = note
        )
    }

    private fun String?.orUnknown(): String = this?.takeIf { it.isNotBlank() } ?: "Bilinmiyor"

    private fun describeDeviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Telefon hoparlörü"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Telefon ahizesi"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Kablolu kulaklık"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Kablolu mikrofonlu kulaklık"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth ses çıkışı"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth kulaklık / ahizesiz"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE kulaklık"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE hoparlör"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB ses çıkışı"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI ses çıkışı"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Hat çıkışı"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX çıkışı"
        else -> "Diğer ses çıkışı"
    }
}
