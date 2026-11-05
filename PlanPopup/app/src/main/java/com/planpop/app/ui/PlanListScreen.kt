package com.planpop.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.isSpecific
import com.planpop.app.data.isSpecificExpired
import com.planpop.app.data.specificDateOrNull
import com.planpop.app.data.specificDateTimeOrNull
import com.planpop.app.util.PermissionStatus
import com.planpop.app.util.formatPlanTime
import com.planpop.app.util.formatTimelineDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class PlanSection(val title: String) {
    TODAY("今天"),
    TOMORROW("明天"),
    FUTURE("未来"),
    EXPIRED("已过期")
}

private data class TimelineGroup(
    val section: PlanSection,
    val plans: List<PlanEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanListScreen(
    plans: List<PlanEntity>,
    completedPlanIds: Set<Long>,
    permissions: PermissionStatus,
    onAdd: () -> Unit,
    onEdit: (PlanEntity) -> Unit,
    onDelete: (PlanEntity) -> Unit,
    onToggleEnabled: (PlanEntity, Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onTestReminder: () -> Unit
) {
    var planToDelete by remember { mutableStateOf<PlanEntity?>(null) }
    val today = LocalDate.now()
    val now = LocalDateTime.now()
    val dateLabel = today.format(
        DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
    )
    val timeline = buildTimeline(plans, today, now)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "我的计划",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onTestReminder) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "测试提醒"
                        )
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "权限与设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("添加计划") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                PlanSummary(
                    total = plans.size,
                    completed = plans.count { it.id in completedPlanIds }
                )
            }

            if (!permissions.allCoreGranted || !permissions.batteryUnrestricted) {
                item(key = "permission-warning") {
                    PermissionWarning(
                        permissions = permissions,
                        onClick = onOpenPermissions
                    )
                }
            }

            if (plans.isEmpty()) {
                item(key = "empty") {
                    EmptyPlans(onAdd = onAdd)
                }
            } else {
                timeline.forEach { group ->
                    item(key = "section-${group.section.name}") {
                        TimelineSectionHeader(
                            section = group.section,
                            count = group.plans.size
                        )
                    }
                    items(items = group.plans, key = { "plan-${it.id}" }) { plan ->
                        PlanCard(
                            plan = plan,
                            completedToday = plan.id in completedPlanIds,
                            today = today,
                            now = now,
                            onEdit = { onEdit(plan) },
                            onDelete = { planToDelete = plan },
                            onToggleEnabled = { enabled -> onToggleEnabled(plan, enabled) }
                        )
                    }
                }
            }

            item(key = "bottom-space") {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }
    }

    planToDelete?.let { plan ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("删除这条计划？") },
            text = {
                Text("「${plan.title}」将不再提醒，相关的完成记录也会一起删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        planToDelete = null
                        onDelete(plan)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun buildTimeline(
    plans: List<PlanEntity>,
    today: LocalDate,
    now: LocalDateTime
): List<TimelineGroup> {
    return PlanSection.values().mapNotNull { section ->
        val sectionPlans = plans
            .filter { sectionFor(it, today, now) == section }
            .sortedWith(comparatorFor(section, today))
        if (sectionPlans.isEmpty()) null else TimelineGroup(section, sectionPlans)
    }
}

private fun sectionFor(
    plan: PlanEntity,
    today: LocalDate,
    now: LocalDateTime
): PlanSection {
    // DAILY plans always belong to "today"; they are not duplicated into every
    // future section.
    if (!plan.isSpecific) return PlanSection.TODAY

    val dateTime = plan.specificDateTimeOrNull() ?: return PlanSection.EXPIRED
    if (plan.triggeredAt != null || !dateTime.isAfter(now)) {
        return PlanSection.EXPIRED
    }

    return when {
        dateTime.toLocalDate() == today -> PlanSection.TODAY
        dateTime.toLocalDate() == today.plusDays(1) -> PlanSection.TOMORROW
        dateTime.toLocalDate().isAfter(today) -> PlanSection.FUTURE
        else -> PlanSection.EXPIRED
    }
}

private fun comparatorFor(
    section: PlanSection,
    today: LocalDate
): Comparator<PlanEntity> {
    return if (section == PlanSection.EXPIRED) {
        compareByDescending<PlanEntity> { it.specificDateOrNull() ?: LocalDate.MIN }
            .thenByDescending { it.hour * 60 + it.minute }
    } else {
        compareBy<PlanEntity> {
            if (it.isSpecific) it.specificDateOrNull() ?: today else today
        }.thenBy { it.hour * 60 + it.minute }
    }
}

@Composable
private fun TimelineSectionHeader(
    section: PlanSection,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (section == PlanSection.EXPIRED) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$count 条",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun PlanSummary(total: Int, completed: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (total == 0) "每日计划和指定日期计划都会显示在这里" else "共 $total 条计划",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        if (completed > 0) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "今日完成 $completed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PermissionWarning(
    permissions: PermissionStatus,
    onClick: () -> Unit
) {
    val issues = buildList {
        if (!permissions.overlay) {
            add("悬浮窗未开启，将降级为高优先级通知弹窗")
        }
        if (!permissions.notifications) {
            add("通知权限未开启，降级提醒可能收不到")
        }
        if (!permissions.exactAlarms) {
            add("精确闹钟未开启，提醒可能被系统延后")
        }
        if (!permissions.fullScreenIntent) {
            add("锁屏全屏通知未开启，锁屏时可能只显示普通通知")
        }
        if (!permissions.batteryUnrestricted) {
            add("建议将 App 加入电池优化白名单")
        }
    }
    if (issues.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "让提醒更可靠",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = issues.joinToString(separator = "\n") { "• $it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "去设置",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PlanCard(
    plan: PlanEntity,
    completedToday: Boolean,
    today: LocalDate,
    now: LocalDateTime,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val specific = plan.isSpecific
    val date = plan.specificDateOrNull()
    val expired = plan.isSpecificExpired(now)
    val inactive = !plan.enabled || expired
    val contentAlpha = if (inactive) 0.55f else 1f
    val dateText = if (specific) {
        date?.let { formatTimelineDate(it, today) } ?: "日期缺失"
    } else {
        "每天"
    }
    val statusText = when {
        completedToday -> "今日已完成"
        expired && plan.triggeredAt != null -> "已失效"
        expired -> "已过期"
        !plan.enabled -> "已停用"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (inactive) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (inactive) 0.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.width(92.dp)
                ) {
                    Text(
                        text = formatPlanTime(plan.hour, plan.minute),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (inactive) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plan.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (statusText != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            PlanStatusChip(
                                text = statusText,
                                completed = completedToday
                            )
                        }
                    }
                    if (plan.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = plan.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (expired) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "单次提醒已结束，可编辑日期后重新启用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else if (!plan.enabled) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "已停用，不会触发提醒",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = plan.enabled && !expired,
                    enabled = !expired,
                    onCheckedChange = onToggleEnabled
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun PlanStatusChip(
    text: String,
    completed: Boolean
) {
    Surface(
        color = if (completed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = CircleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (completed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyPlans(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "还没有计划",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "可以添加「每天 8:00 喝水」，\n也可以指定 11月10日 08:00 只提醒一次。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建第一条计划")
            }
        }
    }
}
