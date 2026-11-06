package com.ultraboost.probe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.ultraboost.probe.audio.DeviceReport
import com.ultraboost.probe.audio.DeviceScanner
import com.ultraboost.probe.audio.EffectInfo
import com.ultraboost.probe.audio.ProbeEffect
import com.ultraboost.probe.audio.ProbeTonePlayer

private val Background = Color(0xFF070B09)
private val SurfaceDark = Color(0xFF111914)
private val SurfaceRaised = Color(0xFF17221B)
private val Neon = Color(0xFF35F58B)
private val Muted = Color(0xFF9AB4A3)
private val TextMain = Color(0xFFF0F7F2)
private val Warning = Color(0xFFFFC66D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltraBoostProbeApp() }
    }
}

@Composable
private fun UltraBoostProbeApp() {
    val context = LocalContext.current
    val player = remember { ProbeTonePlayer() }
    var report by remember { mutableStateOf(DeviceScanner.scan(context)) }
    var isScanning by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var selectedEffect by remember { mutableStateOf(ProbeEffect.LOUDNESS) }
    var testMessage by remember { mutableStateOf("Test sesini başlat, ardından bir efekt seçip uygula.") }
    var testMessageIsError by remember { mutableStateOf(false) }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.stop()
                isPlaying = false
                testMessage = "Uygulama arka plana geçtiği için test sesi durduruldu."
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            player.stop()
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon,
            onPrimary = Color(0xFF052012),
            secondary = Color(0xFF8BEAB4),
            background = Background,
            surface = SurfaceDark,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0C1710), Background, Background)
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Header()
                DeviceCard(report)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            isScanning = true
                            report = DeviceScanner.scan(context)
                            isScanning = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Neon,
                            contentColor = Color(0xFF052012)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isScanning) "TARANIYOR…" else "CİHAZI TEKRAR TARA", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "UltraBoost Probe cihaz raporu")
                                putExtra(Intent.EXTRA_TEXT, report.toShareText())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Raporu paylaş"))
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("RAPOR")
                    }
                }

                SectionHeading("SES MOTORU TARAMASI", "Android tarafından listelenen efekt motorları")
                report.effects.forEach { info -> EffectCard(info) }

                SectionHeading("SES ÇIKIŞLARI", "Şu anda Android'in bildirdiği çıkış aygıtları")
                OutputsCard(report)

                SectionHeading("DAHİLİ SES TESTİ", "Efektleri yalnızca UltraBoost Probe test sesinde dene")
                TestPanel(
                    isPlaying = isPlaying,
                    selectedEffect = selectedEffect,
                    message = testMessage,
                    isError = testMessageIsError,
                    onStart = {
                        try {
                            val session = player.start()
                            isPlaying = true
                            testMessageIsError = false
                            testMessage = "Düşük seviyeli test sesi çalışıyor. Oturum: $session"
                        } catch (error: Exception) {
                            isPlaying = false
                            testMessageIsError = true
                            testMessage = error.message ?: "Test sesi başlatılamadı."
                        }
                    },
                    onStop = {
                        player.stop()
                        isPlaying = false
                        testMessageIsError = false
                        testMessage = "Test sesi durduruldu."
                    },
                    onSelectEffect = { selectedEffect = it },
                    onApply = {
                        val result = player.apply(selectedEffect)
                        testMessage = result
                        testMessageIsError = result.startsWith("Efekt") &&
                            (result.contains("başarısız") || result.contains("edilemedi") || result.contains("kontrol bu uygulamaya"))
                    }
                )

                InfoCard(
                    title = "ÖNEMLİ: BU TARAMA NEYİ KANITLAR?",
                    body = "Efektin listede görünmesi, cihazda ilgili efekt motorunun kayıtlı olduğunu gösterir. Dahili testte başarılı olması ise efektin Probe'un kendi ses oturumuna bağlanabildiğini gösterir. Bunlar YouTube, oyunlar veya tüm telefon sesine sistem genelinde etki edebileceğini kanıtlamaz."
                )

                Text(
                    "UltraBoost Probe 0.1.0  •  Reklamsız tanılama aracı",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ULTRABOOST", color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("PROBE  /  AUDIO DIAGNOSTICS", color = Neon, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF173322), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("U", color = Neon, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DeviceCard(report: DeviceReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(1.dp, Color(0xFF28513A)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("TEST CİHAZI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("API ${report.apiLevel}", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("${report.manufacturer} ${report.model}", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Android ${report.androidVersion}", color = Muted, fontSize = 13.sp)
            if (report.scanNote != null) {
                Text(report.scanNote, color = Warning, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Neon, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EffectCard(info: EffectInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, if (info.available) Color(0xFF28513A) else Color(0xFF26332B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(10.dp).background(if (info.available) Neon else Color(0xFF58645C), CircleShape)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(info.effect.title, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(info.effect.detail, color = Muted, fontSize = 11.sp)
                if (info.available && info.implementation.isNotBlank()) {
                    Text("Uygulayıcı: ${info.implementation}", color = Color(0xFF82C99C), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                if (info.available) "BULUNDU" else "YOK",
                color = if (info.available) Neon else Muted,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun OutputsCard(report: DeviceReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (report.outputs.isEmpty()) {
                Text("Android çıkış aygıtı listesi boş döndü.", color = Muted, fontSize = 12.sp)
            } else {
                report.outputs.forEachIndexed { index, output ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(output.name, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(output.type, color = Muted, fontSize = 11.sp)
                        }
                        Text("${index + 1}", color = Neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (index != report.outputs.lastIndex) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26352B)))
                    }
                }
            }
        }
    }
}

@Composable
private fun TestPanel(
    isPlaying: Boolean,
    selectedEffect: ProbeEffect,
    message: String,
    isError: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelectEffect: (ProbeEffect) -> Unit,
    onApply: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised),
        border = BorderStroke(1.dp, Color(0xFF28513A)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Önce telefon sesini düşük seviyeye getir. Test tonu düşük seviyededir; yine de sesi kulağına yakın kulaklıkla deneme.",
                color = Warning,
                fontSize = 11.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onStart,
                    enabled = !isPlaying,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color(0xFF052012)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("TESTİ BAŞLAT", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onStop,
                    enabled = isPlaying,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("DURDUR") }
            }

            Text("UYGULANACAK EFEKT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProbeEffect.entries.chunked(2).forEach { rowEffects ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        rowEffects.forEach { effect ->
                            FilterChip(
                                selected = selectedEffect == effect,
                                onClick = { onSelectEffect(effect) },
                                label = { Text(effect.title, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowEffects.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Button(
                onClick = onApply,
                enabled = isPlaying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244B34), contentColor = Neon),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SEÇİLİ EFEKTİ TEST SESİNE UYGULA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Text(message, color = if (isError) Warning else Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171A14)),
        border = BorderStroke(1.dp, Color(0xFF58472B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Warning, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(body, color = Color(0xFFD0D9D1), fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
