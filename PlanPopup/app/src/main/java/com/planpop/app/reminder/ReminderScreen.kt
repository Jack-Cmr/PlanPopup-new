package com.planpop.app.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.planpop.app.data.PlanEntity
import com.planpop.app.data.isSpecific
import com.planpop.app.data.specificDateOrNull
import com.planpop.app.util.formatPlanTime
import com.planpop.app.util.formatTimelineDate

@Composable
fun ReminderRoute(
    planId: Long,
    loadPlan: suspend (Long) -> PlanEntity?,
    onComplete: (PlanEntity) -> Unit,
    onSnooze: (PlanEntity) -> Unit,
    onClose: () -> Unit,
    onMissing: () -> Unit
) {
    var plan by remember(planId) { mutableStateOf<PlanEntity?>(null) }
    var loading by remember(planId) { mutableStateOf(true) }
    var actionInProgress by remember(planId) { mutableStateOf(false) }

    LaunchedEffect(planId) {
        loading = true
        plan = runCatching { loadPlan(planId) }.getOrNull()
        loading = false
        if (plan == null) onMissing()
    }

    BackHandler {
        if (!actionInProgress) onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xD9081514),
                        Color(0xF2081514)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> {
                CircularProgressIndicator(color = Color.White)
            }

            plan != null -> {
                ReminderDialog(
                    plan = plan!!,
                    actionInProgress = actionInProgress,
                    onComplete = {
                        if (!actionInProgress) {
                            actionInProgress = true
                            onComplete(plan!!)
                        }
                    },
                    onSnooze = {
                        if (!actionInProgress) {
                            actionInProgress = true
                            onSnooze(plan!!)
                        }
                    },
                    onClose = {
                        if (!actionInProgress) {
                            actionInProgress = true
                            onClose()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ReminderDialog(
    plan: PlanEntity,
    actionInProgress: Boolean,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onClose: () -> Unit
) {
    val scheduleLabel = if (plan.isSpecific) {
        val date = plan.specificDateOrNull()
        if (date != null) {
            "指定日期 · ${formatTimelineDate(date)} ${formatPlanTime(plan.hour, plan.minute)}"
        } else {
            "指定日期 · ${formatPlanTime(plan.hour, plan.minute)}"
        }
    } else {
        "每日计划 · ${formatPlanTime(plan.hour, plan.minute)}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(74.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "现在该做",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plan.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (plan.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = plan.note,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.size(7.dp))
                Text(
                    text = scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onComplete,
                enabled = !actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("完成", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onSnooze,
                enabled = !actionInProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Snooze, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("稍后 10 分钟", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onClose,
                enabled = !actionInProgress,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("关闭", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
