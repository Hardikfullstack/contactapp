package com.example.contactapp.ui.features.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ui.components.CommonHeader
import com.example.contactapp.ui.components.ContactAvatarImage
import com.example.contactapp.ui.components.HeaderActionButton
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.getAvatarColor
import kotlin.math.roundToInt

private val IncomingColor = Color(0xFF2196F3)
private val OutgoingColor = Color(0xFF4CAF50)
private val MissedColor = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var activityViewMode by remember { mutableStateOf(ActivityViewMode.HOUR) }
    var selectedPeriod by remember { mutableStateOf<PeriodStat?>(null) }
    var selectedPeriodIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .statusBarsPadding()
        ) {
            CommonHeader(
                title = stringResource(R.string.call_analytics),
                onBackClick = onBack,
                actions = {
                    HeaderActionButton(
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.refresh() }
                    )
                }
            )

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryGreen)
                    }
                }
                !uiState.hasData -> {
                    EmptyAnalyticsState()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Summary Grid
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard(
                                        label = stringResource(R.string.total_calls),
                                        value = uiState.totalCalls.toString(),
                                        icon = Icons.Default.Call,
                                        color = IncomingColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = stringResource(R.string.talk_time),
                                        value = uiState.totalDuration,
                                        icon = Icons.Default.Schedule,
                                        color = OutgoingColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    StatCard(
                                        label = stringResource(R.string.missed_calls),
                                        value = uiState.missedCount.toString(),
                                        icon = Icons.Default.CallMissed,
                                        color = MissedColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        label = stringResource(R.string.avg_duration),
                                        value = uiState.averageDuration,
                                        icon = Icons.Default.QueryStats,
                                        color = Color(0xFF9C27B0),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Call Breakdown
                        item {
                            AnalyticsSection(title = stringResource(R.string.call_breakdown)) {
                                CallBreakdownBar(
                                    incoming = uiState.incomingCount,
                                    outgoing = uiState.outgoingCount,
                                    missed = uiState.missedCount,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                )
                                BreakdownLegendRow(
                                    color = IncomingColor,
                                    label = stringResource(R.string.incoming),
                                    count = uiState.incomingCount,
                                    total = uiState.totalCalls
                                )
                                BreakdownLegendRow(
                                    color = OutgoingColor,
                                    label = stringResource(R.string.outgoing),
                                    count = uiState.outgoingCount,
                                    total = uiState.totalCalls
                                )
                                BreakdownLegendRow(
                                    color = MissedColor,
                                    label = stringResource(R.string.missed),
                                    count = uiState.missedCount,
                                    total = uiState.totalCalls
                                )
                            }
                        }

                        // Activity Chart
                        item {
                            val stats = when (activityViewMode) {
                                ActivityViewMode.HOUR -> uiState.hourlyStats
                                ActivityViewMode.DAY -> uiState.dailyStats
                                ActivityViewMode.MONTH -> uiState.monthlyStats
                            }

                            AnalyticsSection(title = stringResource(R.string.daily_activity)) {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                ) {
                                    ActivityViewMode.entries.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = activityViewMode == mode,
                                            onClick = {
                                                activityViewMode = mode
                                                selectedPeriod = null
                                                selectedPeriodIndex = null
                                            },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = ActivityViewMode.entries.size
                                            ),
                                            label = {
                                                Text(
                                                    when (mode) {
                                                        ActivityViewMode.HOUR -> stringResource(R.string.view_hour)
                                                        ActivityViewMode.DAY -> stringResource(R.string.view_day)
                                                        ActivityViewMode.MONTH -> stringResource(R.string.view_month)
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }

                                ActivityChart(
                                    stats = stats,
                                    selectedIndex = selectedPeriodIndex,
                                    onBarClick = { index ->
                                        selectedPeriodIndex = index
                                        selectedPeriod = stats.getOrNull(index)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .padding(vertical = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val axisLabels = if (activityViewMode == ActivityViewMode.HOUR) {
                                        listOfNotNull(stats.getOrNull(0), stats.getOrNull(6), stats.getOrNull(12), stats.getOrNull(18))
                                            .map { it.label }
                                    } else {
                                        stats.map { it.label }
                                    }
                                    axisLabels.forEach { label ->
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.tap_bar_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        // Top Callers
                        if (uiState.topCallers.isNotEmpty()) {
                            item {
                                AnalyticsSection(title = stringResource(R.string.top_contacts)) {
                                    uiState.topCallers.forEach { caller ->
                                        TopCallerItem(caller = caller)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    selectedPeriod?.let { period ->
        PeriodDetailDialog(
            stat = period,
            onDismiss = {
                selectedPeriod = null
                selectedPeriodIndex = null
            }
        )
    }
}

@Composable
fun EmptyAnalyticsState() {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_call_history),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.no_call_history_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AnalyticsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun CallBreakdownBar(
    incoming: Int,
    outgoing: Int,
    missed: Int,
    modifier: Modifier = Modifier
) {
    val total = (incoming + outgoing + missed).coerceAtLeast(1)
    Row(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp)),
    ) {
        if (incoming > 0) Box(Modifier.weight(incoming.toFloat()).fillMaxHeight().background(IncomingColor))
        if (outgoing > 0) Box(Modifier.weight(outgoing.toFloat()).fillMaxHeight().background(OutgoingColor))
        if (missed > 0) Box(Modifier.weight(missed.toFloat()).fillMaxHeight().background(MissedColor))
        if (incoming + outgoing + missed == 0) {
            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
fun BreakdownLegendRow(color: Color, label: String, count: Int, total: Int) {
    val percent = if (total > 0) ((count.toFloat() / total) * 100).roundToInt() else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count ($percent%)",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActivityChart(
    stats: List<PeriodStat>,
    selectedIndex: Int?,
    onBarClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = PrimaryGreen
    val maxCalls = (stats.maxOfOrNull { it.totalCalls } ?: 0).coerceAtLeast(1)

    fun barSlotWidth(totalWidth: Float): Pair<Float, Float> {
        val barWidth = totalWidth / (stats.size * 1.5f)
        val space = if (stats.size > 1) (totalWidth - (barWidth * stats.size)) / (stats.size - 1) else 0f
        return barWidth to space
    }

    Canvas(
        modifier = modifier.pointerInput(stats) {
            detectTapGestures { offset ->
                if (stats.isEmpty()) return@detectTapGestures
                val (barWidth, space) = barSlotWidth(size.width.toFloat())
                val slot = (barWidth + space).coerceAtLeast(1f)
                val index = (offset.x / slot).toInt().coerceIn(0, stats.size - 1)
                onBarClick(index)
            }
        }
    ) {
        val width = size.width
        val height = size.height
        val (barWidth, space) = barSlotWidth(width)

        stats.forEachIndexed { index, stat ->
            val value = stat.totalCalls / maxCalls.toFloat()
            val barHeight = height * value.coerceIn(0.05f, 1f)
            val isDimmed = selectedIndex != null && selectedIndex != index
            drawRoundRect(
                color = if (isDimmed) barColor.copy(alpha = 0.35f) else barColor,
                topLeft = Offset(index * (barWidth + space), height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        }
    }
}

@Composable
fun PeriodDetailDialog(stat: PeriodStat, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.period_activity_title, stat.fullLabel),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                PeriodDetailRow(stringResource(R.string.total_calls), stat.totalCalls.toString())
                PeriodDetailRow(stringResource(R.string.incoming), stat.incomingCount.toString())
                PeriodDetailRow(stringResource(R.string.outgoing), stat.outgoingCount.toString())
                PeriodDetailRow(stringResource(R.string.missed), stat.missedCount.toString())
                PeriodDetailRow(stringResource(R.string.talk_time), stat.totalDuration)

                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun PeriodDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TopCallerItem(caller: TopCaller) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(getAvatarColor(caller.name)),
            contentAlignment = Alignment.Center
        ) {
            if (caller.photoUri != null) {
                ContactAvatarImage(
                    photoUri = caller.photoUri,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = caller.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = caller.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.calls_count, caller.callCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = caller.totalDuration,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = PrimaryGreen
        )
    }
}
