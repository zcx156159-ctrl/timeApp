package com.example.timetable

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import com.example.timetable.data.CloudRepository
import com.example.timetable.data.CourseRepository
import com.example.timetable.data.createSettings
import com.example.timetable.ui.TimetableScreen
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import timetable_kmp.composeapp.generated.resources.Res
import timetable_kmp.composeapp.generated.resources.notosanssc

/** 基于默认排版，把所有样式的字体替换为内置中文字体（黑体）。 */
private fun cnTypography(cnFontFamily: FontFamily): Typography {
    val base = Typography()
    fun withCn(style: TextStyle) = style.copy(fontFamily = cnFontFamily)
    return Typography(
        displayLarge = withCn(base.displayLarge),
        displayMedium = withCn(base.displayMedium),
        displaySmall = withCn(base.displaySmall),
        headlineLarge = withCn(base.headlineLarge),
        headlineMedium = withCn(base.headlineMedium),
        headlineSmall = withCn(base.headlineSmall),
        titleLarge = withCn(base.titleLarge),
        titleMedium = withCn(base.titleMedium),
        titleSmall = withCn(base.titleSmall),
        bodyLarge = withCn(base.bodyLarge),
        bodyMedium = withCn(base.bodyMedium),
        bodySmall = withCn(base.bodySmall),
        labelLarge = withCn(base.labelLarge),
        labelMedium = withCn(base.labelMedium),
        labelSmall = withCn(base.labelSmall),
    )
}

/** 三端共用的应用入口：主题 + 状态 + 界面。 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun App() {
    val cnFontFamily = FontFamily(Font(Res.font.notosanssc))
    val state = remember {
        TimetableState(CourseRepository(createSettings()), CloudRepository())
    }
    val colorScheme = if (state.darkTheme) {
        darkColorScheme(primary = Color(0xFF5C6BC0), secondary = Color(0xFFFFB74D))
    } else {
        lightColorScheme(primary = Color(0xFF3F51B5), secondary = Color(0xFFFF9800))
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = cnTypography(cnFontFamily),
    ) {
        TimetableScreen(state)
    }
}
