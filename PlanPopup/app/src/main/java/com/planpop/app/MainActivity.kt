package com.planpop.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planpop.app.data.PlanEntity
import com.planpop.app.reminder.ReminderActivity
import com.planpop.app.ui.PermissionGuideScreen
import com.planpop.app.ui.PlanEditorScreen
import com.planpop.app.ui.PlanListScreen
import com.planpop.app.ui.PlanViewModel
import com.planpop.app.ui.theme.PlanPopupTheme
import com.planpop.app.util.openBatteryOptimizationSettings
import com.planpop.app.util.openExactAlarmSettings
import com.planpop.app.util.openFullScreenIntentSettings
import com.planpop.app.util.openNotificationSettings
import com.planpop.app.util.openOverlaySettings
import com.planpop.app.util.readPermissionStatus
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PlanPopupApplication

        setContent {
            PlanPopupTheme {
                val viewModel: PlanViewModel = viewModel(
                    factory = PlanViewModel.factory(app)
                )
                val plans by viewModel.plans.collectAsStateWithLifecycle()
                val completedToday by viewModel.completedToday.collectAsStateWithLifecycle()
                val onboarding by app.settingsStore.onboardingCompleted
                    .collectAsStateWithLifecycle<Boolean?>(initialValue = null)

                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()

                var permissions by remember {
                    mutableStateOf(context.readPermissionStatus())
                }
                var showPermissionScreen by rememberSaveable { mutableStateOf(false) }
                var showEditor by rememberSaveable { mutableStateOf(false) }
                var editingPlanId by rememberSaveable { mutableStateOf<Long?>(null) }
                var notificationPermissionRequested by rememberSaveable {
                    mutableStateOf(false)
                }
                var onboardingFinishing by remember { mutableStateOf(false) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {
                    permissions = context.readPermissionStatus()
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissions = context.readPermissionStatus()
                            viewModel.refreshToday()
                            viewModel.rescheduleAll()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.rescheduleAll()
                }

                // Keep the "today" completion badges correct if the app stays
                // open across midnight.
                LaunchedEffect(Unit) {
                    while (true) {
                        val now = ZonedDateTime.now()
                        val nextMidnight = now.toLocalDate()
                            .plusDays(1)
                            .atStartOfDay(now.zone)
                        delay(
                            Duration.between(now, nextMidnight)
                                .toMillis()
                                .coerceAtLeast(1_000L)
                        )
                        viewModel.refreshToday()
                    }
                }

                LaunchedEffect(viewModel) {
                    viewModel.messages.collect { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }

                val requestNotifications: () -> Unit = {
                    val denied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED

                    if (denied) {
                        val shouldAskAgain = !notificationPermissionRequested ||
                            shouldShowRequestPermissionRationale(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        if (shouldAskAgain) {
                            notificationPermissionRequested = true
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else {
                            context.openNotificationSettings()
                        }
                    } else {
                        context.openNotificationSettings()
                    }
                }

                val finishOnboarding: () -> Unit = {
                    if (!onboardingFinishing) {
                        onboardingFinishing = true
                        scope.launch {
                            try {
                                app.settingsStore.setOnboardingCompleted(true)
                                viewModel.rescheduleAll()
                            } finally {
                                onboardingFinishing = false
                            }
                        }
                    }
                }

                val editingPlan: PlanEntity? = editingPlanId?.let { id ->
                    plans.firstOrNull { it.id == id }
                }

                when {
                    onboarding == null -> LoadingScreen()

                    onboarding == false -> {
                        PermissionGuideScreen(
                            status = permissions,
                            isFirstRun = true,
                            onBack = {},
                            onRequestNotifications = requestNotifications,
                            onOpenExactAlarms = { context.openExactAlarmSettings() },
                            onOpenOverlay = { context.openOverlaySettings() },
                            onOpenFullScreenIntent = { context.openFullScreenIntentSettings() },
                            onOpenBattery = { context.openBatteryOptimizationSettings() },
                            onFinish = finishOnboarding
                        )
                    }

                    showPermissionScreen -> {
                        BackHandler { showPermissionScreen = false }
                        PermissionGuideScreen(
                            status = permissions,
                            isFirstRun = false,
                            onBack = { showPermissionScreen = false },
                            onRequestNotifications = requestNotifications,
                            onOpenExactAlarms = { context.openExactAlarmSettings() },
                            onOpenOverlay = { context.openOverlaySettings() },
                            onOpenFullScreenIntent = { context.openFullScreenIntentSettings() },
                            onOpenBattery = { context.openBatteryOptimizationSettings() },
                            onFinish = { showPermissionScreen = false }
                        )
                    }

                    showEditor -> {
                        BackHandler {
                            showEditor = false
                            editingPlanId = null
                        }
                        if (editingPlanId != null && editingPlan == null) {
                            LaunchedEffect(plans, editingPlanId) {
                                val id = editingPlanId
                                if (id != null && plans.isNotEmpty() &&
                                    plans.none { it.id == id }
                                ) {
                                    showEditor = false
                                    editingPlanId = null
                                }
                            }
                            LoadingScreen()
                        } else {
                            PlanEditorScreen(
                                initialPlan = editingPlan,
                                onBack = {
                                    showEditor = false
                                    editingPlanId = null
                                },
                                onSave = { plan ->
                                    viewModel.savePlan(plan) {
                                        showEditor = false
                                        editingPlanId = null
                                    }
                                }
                            )
                        }
                    }

                    else -> {
                        PlanListScreen(
                            plans = plans,
                            completedPlanIds = completedToday,
                            permissions = permissions,
                            onAdd = {
                                editingPlanId = null
                                showEditor = true
                            },
                            onEdit = { plan ->
                                editingPlanId = plan.id
                                showEditor = true
                            },
                            onDelete = viewModel::deletePlan,
                            onToggleEnabled = viewModel::setEnabled,
                            onOpenPermissions = { showPermissionScreen = true },
                            onTestReminder = {
                                val plan = plans.firstOrNull { it.enabled } ?: plans.firstOrNull()
                                if (plan == null) {
                                    Toast.makeText(
                                        context,
                                        "请先添加一条计划，再测试提醒",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    startActivity(
                                        ReminderActivity.createIntent(this@MainActivity, plan.id)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
