/*
 * НАЗНАЧЕНИЕ
 * Единая тема оформления приложения (Compose Desktop) и набор базовых
 * переиспользуемых layout-композаблов. Задаёт палитру, типографику, формы и
 * оборачивает контент в MaterialTheme. Используется всеми экранами и
 * компонентами UI как единый источник стиля.
 *
 * ЧТО ВНУТРИ
 * object AppColors — централизованная палитра (сайдбар, контент, текст, акценты,
 * статусы, бренд VK, риски). Приватные LightColors/AppTypography/AppShapes —
 * настройки Material. @Composable AppTheme — корневой провайдер темы.
 * Базовые @Composable-обёртки: SectionCard (карточка-секция), ScreenHeader
 * (заголовок экрана с действиями), StatusBadge (статус-бейдж), EmptyStateMessage
 * (заглушка пустого состояния).
 *
 * ФРЕЙМВОРКИ
 * Compose Desktop / Material (lightColors, Typography, Shapes, MaterialTheme).
 * Цвета — литеральные ARGB, согласованы с остальными UI-компонентами.
 *
 * СВЯЗИ
 * AppColors импортируется во всех компонентах ui.components.
 */
package com.example.lomanalyzer.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Палитра ──────────────────────────────────────────────────────────────────

/** Централизованная палитра приложения: все цвета UI задаются здесь. */
object AppColors {
    // Боковая панель (sidebar)
    val sidebarBg = Color(0xFF0F172A)
    val sidebarSurface = Color(0xFF1E293B)
    val sidebarText = Color(0xFF94A3B8)
    val sidebarTextActive = Color.White
    val sidebarAccent = Color(0xFF3B82F6)

    // Область контента
    val contentBg = Color(0xFFF1F5F9)
    val surface = Color.White
    val surfaceVariant = Color(0xFFF8FAFC)

    // Текст (основной/вторичный/третичный)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF475569)
    val textTertiary = Color(0xFF94A3B8)

    // Акцентные цвета
    val primary = Color(0xFF3B82F6)
    val primaryDark = Color(0xFF1D4ED8)
    val primaryLight = Color(0xFFDBEAFE)
    val secondary = Color(0xFF10B981)
    val secondaryLight = Color(0xFFD1FAE5)

    // Статусы (успех/предупреждение/ошибка/инфо) и их светлые фоны
    val success = Color(0xFF10B981)
    val successLight = Color(0xFFD1FAE5)
    val warning = Color(0xFFF59E0B)
    val warningLight = Color(0xFFFEF3C7)
    val error = Color(0xFFEF4444)
    val errorLight = Color(0xFFFEE2E2)
    val info = Color(0xFF3B82F6)
    val infoLight = Color(0xFFDBEAFE)

    // Фирменные цвета ВКонтакте
    val vkBlue = Color(0xFF0077FF)
    val vkBlueDark = Color(0xFF0066DD)

    // Границы карточек и разделители
    val border = Color(0xFFE2E8F0)
    val divider = Color(0xFFE2E8F0)

    // Цвета уровней риска (фон + граница) для индикаторов
    val riskHigh = Color(0xFFFEE2E2)
    val riskHighBorder = Color(0xFFFCA5A5)
    val riskMedium = Color(0xFFFEF3C7)
    val riskMediumBorder = Color(0xFFFCD34D)
    val riskLow = Color(0xFFD1FAE5)
    val riskLowBorder = Color(0xFF6EE7B7)
    val riskMinimal = Color(0xFFF1F5F9)
}

// ── Переопределения Material ─────────────────────────────────────────────────

/** Светлая цветовая схема Material, собранная из палитры AppColors. */
private val LightColors = lightColors(
    primary = AppColors.primary,
    primaryVariant = AppColors.primaryDark,
    secondary = AppColors.secondary,
    secondaryVariant = AppColors.secondary,
    background = AppColors.contentBg,
    surface = AppColors.surface,
    error = AppColors.error,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AppColors.textPrimary,
    onSurface = AppColors.textPrimary,
    onError = Color.White,
)

/** Типографика приложения: размеры/начертания заголовков, текста и подписей. */
private val AppTypography = Typography(
    h4 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp, color = AppColors.textPrimary),
    h5 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 0.sp, color = AppColors.textPrimary),
    h6 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.15.sp, color = AppColors.textPrimary),
    subtitle1 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.15.sp, color = AppColors.textPrimary),
    subtitle2 = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.1.sp, color = AppColors.textSecondary),
    body1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp, color = AppColors.textPrimary),
    body2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.25.sp, color = AppColors.textSecondary),
    caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, letterSpacing = 0.4.sp, color = AppColors.textTertiary),
    button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.75.sp),
    overline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.5.sp),
)

/** Скругления углов трёх размеров для компонентов Material. */
private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/**
 * Корневой провайдер темы: оборачивает контент в MaterialTheme с палитрой,
 * типографикой и формами приложения.
 * @param content содержимое, к которому применяется тема.
 */
@Composable
@Suppress("FunctionNaming")
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

// ── Переиспользуемые layout-композаблы ───────────────────────────────────────

/**
 * Карточка-секция: белая карточка со скруглением, тенью и внутренними отступами;
 * располагает содержимое в Column с вертикальными промежутками.
 * @param modifier внешний Modifier.
 * @param content содержимое секции (в контексте ColumnScope).
 */
@Composable
@Suppress("FunctionNaming")
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = 1.dp,
        backgroundColor = AppColors.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * Заголовок экрана: слева — заголовок и опциональный подзаголовок, справа —
 * блок действий (кнопки).
 * @param title заголовок экрана.
 * @param subtitle подзаголовок, опционально.
 * @param modifier внешний Modifier.
 * @param actions кнопки/действия в правой части (в контексте RowScope).
 */
@Composable
@Suppress("FunctionNaming")
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.h5)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.body2, color = AppColors.textSecondary)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * Универсальный статус-бейдж: текст на цветном фоне в скруглённой капсуле.
 * @param text подпись бейджа.
 * @param color цвет текста.
 * @param backgroundColor цвет фона капсулы.
 * @param modifier внешний Modifier.
 */
@Composable
@Suppress("FunctionNaming")
fun StatusBadge(
    text: String,
    color: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Заглушка пустого состояния: по центру — заголовок и пояснительный подзаголовок.
 * Показывается, когда данных ещё нет (например, до завершения анализа).
 * @param title основной текст.
 * @param subtitle пояснение.
 * @param modifier внешний Modifier.
 */
@Composable
@Suppress("FunctionNaming")
fun EmptyStateMessage(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.h6, color = AppColors.textSecondary)
        Text(subtitle, style = MaterialTheme.typography.body2, color = AppColors.textTertiary)
    }
}
