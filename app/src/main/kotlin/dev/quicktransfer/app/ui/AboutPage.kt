// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 lilyzhaun
package dev.quicktransfer.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quicktransfer.app.BuildConfig
import dev.quicktransfer.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal fun aboutIdentityAlpha(progress: Float, stage: Float) = 1f - ((progress - stage) / 0.15f).coerceIn(0f, 1f)

@Composable
internal fun AboutPage(back: () -> Unit, license: () -> Unit = {}, active: Boolean = LocalRouteActive.current) {
    val colors = uiColors()
    val dark = LocalDarkTheme.current
    val secondaryInk = if (dark) Color(0xFFD0D5E3) else Color(0xFF52515D)
    val summaries = BasicComponentDefaults.summaryColor(color = secondaryInk)
    val appearance = LocalAppearance.current
    val frost = appearance.blur && LocalShaderSupported.current
    val context = LocalContext.current
    val cloud = rememberLayerBackdrop()
    val content = rememberLayerBackdrop { drawRect(colors.canvas); drawContent() }
    val list = rememberLazyListState()
    var identityHeight by remember { mutableIntStateOf(0) }
    var spacerHeight by remember { mutableIntStateOf(1) }
    val progress by remember { derivedStateOf {
        if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset.toFloat() / spacerHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
    } }
    val collapsed = progress >= 1f
    val motion = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val panelBlend = remember(dark) { BlurColors(blendColors = if (dark)
        listOf(BlendColorEntry(Color(0x757A7A7A), BlurBlendMode.Luminosity)) else
        listOf(BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay), BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight))) }
    val header = if (collapsed && frost) Modifier.drawBackdrop(content, shape = { RectangleShape },
        effects = { blur(25f) }, onDrawSurface = { drawRect(colors.canvas.copy(alpha = 0.92f)) })
        else Modifier.background(if (collapsed) colors.canvas else Color.Transparent)
    Scaffold(containerColor = colors.canvas, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = { SmallTopAppBar("关于", modifier = header, color = Color.Transparent,
            titleColor = colors.text.copy(alpha = progress), navigationIcon = {
                IconButton(back, modifier = Modifier.size(48.dp)) { Icon(MiuixIcons.Back, "返回") }
            }) }) { padding ->
        val direction = LocalLayoutDirection.current
        val density = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = padding.calculateStartPadding(direction),
            end = padding.calculateEndPadding(direction)).layerBackdrop(content)) {
            val compact = maxHeight < 520.dp
            val top = padding.calculateTopPadding()
            val bottom = maxOf(LocalBottomInset.current, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            val viewport = (maxHeight - top - bottom).coerceAtLeast(0.dp)
            AboutCloud(active, !collapsed, cloud) { 1f - progress }
            Column(Modifier.fillMaxWidth().padding(top = top + if (compact) 24.dp else 92.dp)
                .padding(horizontal = 24.dp).onSizeChanged { identityHeight = it.height }
                .then(if (progress >= 0.5f) Modifier.clearAndSetSemantics {} else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.ic_sim_transfer), null,
                    Modifier.size(if (compact) 72.dp else 88.dp).graphicsLayer {
                        alpha = aboutIdentityAlpha(progress, 0.35f); scaleX = 0.95f + 0.05f * alpha; scaleY = scaleX
                    }.clip(RoundedCornerShape(22.dp)).background(colorResource(R.color.launcher_background)))
                Text("eSIM transfer", fontSize = if (density.fontScale > 1.5f) 30.sp else 35.sp,
                    lineHeight = 44.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = colors.text,
                    modifier = Modifier.padding(top = 12.dp, bottom = 5.dp).graphicsLayer {
                        alpha = aboutIdentityAlpha(progress, 0.20f); scaleX = 0.95f + 0.05f * alpha; scaleY = scaleX
                    })
                Text("版本 ${BuildConfig.VERSION_NAME}", color = secondaryInk, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer {
                        alpha = aboutIdentityAlpha(progress, 0.05f); scaleX = 0.95f + 0.05f * alpha; scaleY = scaleX
                    })
            }
            LazyColumn(state = list, overscrollEffect = null,
                modifier = Modifier.fillMaxSize()
                    .overScrollVertical(isEnabled = { active && motion > 0f })
                    .then(if (active && appearance.haptics) Modifier.scrollEndHaptic() else Modifier),
                contentPadding = PaddingValues(top = top, bottom = bottom)) {
                item("identity") { Spacer(Modifier.fillMaxWidth().height(with(density) { identityHeight.toDp() } +
                    if (compact) 64.dp else 218.dp).onSizeChanged { spacerHeight = it.height }) }
                item("information") {
                    Column(Modifier.fillMaxWidth().heightIn(min = viewport).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(Modifier.widthIn(max = 960.dp).fillMaxWidth().then(if (frost) Modifier.textureBlur(cloud,
                            shape = RoundedCornerShape(16.dp), blurRadius = 60f, colors = panelBlend) else Modifier),
                            insideMargin = PaddingValues(0.dp), colors = CardDefaults.defaultColors(
                                if (frost) Color.Transparent else colors.surface, Color.Transparent)) {
                            BasicComponent(title = "eSIM 转移", summary = "单机授权，目标设备稍后下载", summaryColor = summaries)
                            ArrowPreference(title = "GitHub", summary = "lilyzhaun/esim-transfer", summaryColor = summaries, onClick = ({
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/lilyzhaun/esim-transfer")))
                            }).takeIf { active })
                            ArrowPreference(title = "许可证与声明", summary = "开源组件与授权说明", summaryColor = summaries, onClick = license.takeIf { active })
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LicensePage(back: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) {
        listOf("LICENSE", "Apache-2.0.txt").flatMap { name ->
            context.assets.open(name).bufferedReader().use { it.readText() }.trim().split(Regex("\\n\\s*\\n"))
        }
    } }
    PageFrame("许可证与声明", onBack = back) {
        item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            BasicComponent(title = "开源组件", summary = "miuix · AndroidX · Kotlin · Shizuku · libxposed · ZXing")
            BasicComponent(title = "流体云", summary = "compose-miuix-ui contributors · Apache-2.0")
        } }
        if (paragraphs == null) item { Text("正在读取许可…", color = uiColors().secondary) }
        else items(paragraphs!!) { paragraph -> SelectionContainer {
            Text(paragraph, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth().padding(8.dp))
        } }
    }
}
