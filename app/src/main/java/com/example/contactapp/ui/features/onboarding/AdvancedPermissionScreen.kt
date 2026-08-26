package com.example.contactapp.ui.features.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.contactapp.R
import com.example.contactapp.ui.theme.PrimaryGreen
import com.example.contactapp.util.CallReliabilityUtils
import com.example.contactapp.util.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PermissionStep {
    OVERLAY, FULL_SCREEN_INTENT, MIUI_PERMISSIONS, MIUI_AUTOSTART, ONEPLUS_AUTOSTART, DONE
}

@Composable
fun AdvancedPermissionScreen(
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context.applicationContext) }

    // Safety net for a confirmed MIUI quirk: Settings.canDrawOverlays() has been observed to
    // report true on this OEM immediately after a fresh install, before the user has ever seen
    // this permission's Settings page — silently skipping the OVERLAY step, with no easy way
    // back to it later, on a device where the underlying grant can later turn out to actually be
    // false (e.g. AfterCallReceiver's own live check has seen it read false in the same session).
    // Forces the step to show at least once on MIUI regardless of what the API claims; set once
    // the user has actually seen/acted on it so subsequent steps aren't blocked forever.
    var hasForcedOverlayStep by remember { mutableStateOf(false) }

    fun computeNextStep(): PermissionStep {
        val canDrawOverlays = Settings.canDrawOverlays(context)
        val forceOverlayOnMiui = CallReliabilityUtils.isMiui() && !hasForcedOverlayStep
        val step = if (!canDrawOverlays || forceOverlayOnMiui) PermissionStep.OVERLAY
        else if (!CallReliabilityUtils.hasFullScreenIntentPermission(context)) PermissionStep.FULL_SCREEN_INTENT
        else if (CallReliabilityUtils.isMiui() && !CallReliabilityUtils.isMiuiBackgroundPopupGranted(context) && !prefs.isMiuiPermissionsCompleted()) PermissionStep.MIUI_PERMISSIONS
        else if (CallReliabilityUtils.isMiui() && !CallReliabilityUtils.isMiuiAutostartGranted(context) && !prefs.isMiuiAutostartCompleted()) PermissionStep.MIUI_AUTOSTART
        else if (CallReliabilityUtils.isOnePlusOrOppo() && !prefs.isOemAutostartCompleted()) PermissionStep.ONEPLUS_AUTOSTART
        else PermissionStep.DONE
        Log.d("AdvPermDebug", "computeNextStep: canDrawOverlays=$canDrawOverlays, forced=$forceOverlayOnMiui, isMiui=${CallReliabilityUtils.isMiui()}, autoPrompted=${prefs.isOverlayPermissionAutoPrompted()} -> $step")
        return step
    }

    var currentStep by remember { mutableStateOf(computeNextStep()) }

    fun checkNextStepAfterOverlay() {
        if (Settings.canDrawOverlays(context)) {
            currentStep = computeNextStep()
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkNextStepAfterOverlay()
    }

    val miuiPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (CallReliabilityUtils.isMiuiBackgroundPopupGranted(context)) {
            prefs.setMiuiPermissionsCompleted()
        }
        checkNextStepAfterOverlay()
    }

    val miuiAutoStartLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (CallReliabilityUtils.isMiuiAutostartGranted(context)) {
            prefs.setMiuiAutostartCompleted()
        }
        checkNextStepAfterOverlay()
    }

    val fullScreenIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkNextStepAfterOverlay()
    }

    val onePlusAutoStartLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        prefs.setOemAutostartCompleted()
        checkNextStepAfterOverlay()
    }

    val coroutineScope = rememberCoroutineScope()

    val startAutoReturnPolling = {
        coroutineScope.launch {
            while (!Settings.canDrawOverlays(context)) {
                delay(300)
            }
            try {
                val returnIntent = Intent(
                    context,
                    Class.forName("${context.packageName}.ui.MainActivity")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(returnIntent)
            } catch (e: Exception) {
                try {
                    val returnIntent2 = Intent(
                        context,
                        Class.forName("${context.packageName}.MainActivity")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(returnIntent2)
                } catch (e2: Exception) {}
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!Settings.canDrawOverlays(context) && !prefs.isOverlayPermissionAutoPrompted()) {
            prefs.setOverlayPermissionAutoPrompted()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayLauncher.launch(intent)
            startAutoReturnPolling()
        } else if (!CallReliabilityUtils.isMiui() || hasForcedOverlayStep) {
            // On MIUI, while the OVERLAY step is being force-shown (see computeNextStep()), don't
            // let this auto-advance past it the instant the screen composes just because the
            // (possibly lying) API already reports true — let the user actually see it and tap
            // through via onPermissionActionClick below, which is what sets hasForcedOverlayStep.
            checkNextStepAfterOverlay()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkNextStepAfterOverlay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == PermissionStep.DONE) {
            onAllPermissionsGranted()
        }
    }

    if (currentStep != PermissionStep.DONE) {
        val isGenericExtraStep = currentStep != PermissionStep.OVERLAY
        val backgroundColor = MaterialTheme.colorScheme.background
        val textColor = MaterialTheme.colorScheme.onBackground
        val descColor = MaterialTheme.colorScheme.secondary

        val strPermissionMiuiTitle = stringResource(R.string.permission_miui_title)
        val strPermissionsRequiredTitle = stringResource(R.string.permissions_required_title)
        val strPermissionOverlayDesc = stringResource(R.string.permission_overlay_desc)
        val strPermissionMiuiDesc = stringResource(R.string.permission_miui_desc)
        val strActionGoToSettings = stringResource(R.string.action_go_to_settings)
        val strActionGrantPermission = stringResource(R.string.action_grant_permission)
        val strActionGrantAutostartPermission = stringResource(R.string.action_grant_autostart_permission)

        val onPermissionActionClick: () -> Unit = {
            when (currentStep) {
                PermissionStep.OVERLAY -> {
                    // Always open the real Settings page here, regardless of what
                    // canDrawOverlays() currently claims — this button can be reached either
                    // because it's genuinely ungranted, or via the MIUI force-show safety net
                    // (where the API may already be lying "true"). Trusting that flag to decide
                    // whether to even open Settings is exactly what silently skipped straight to
                    // the next step before. overlayLauncher's own callback re-checks and advances
                    // once the user actually comes back — no unreliable polling involved, same as
                    // every other OEM step below (MIUI_PERMISSIONS/MIUI_AUTOSTART) already does.
                    hasForcedOverlayStep = true
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    overlayLauncher.launch(intent)
                    startAutoReturnPolling()
                }
                PermissionStep.FULL_SCREEN_INTENT -> {
                    try {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}")
                        )
                        fullScreenIntentLauncher.launch(intent)
                    } catch (e: Exception) {
                        currentStep = computeNextStep()
                    }
                }
                PermissionStep.MIUI_PERMISSIONS -> {
                    try {
                        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
                        intent.setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity"
                        )
                        intent.putExtra("extra_pkgname", context.packageName)
                        miuiPermissionsLauncher.launch(intent)
                    } catch (e: Exception) {
                        prefs.setMiuiPermissionsCompleted()
                        currentStep = computeNextStep()
                    }
                }
                PermissionStep.MIUI_AUTOSTART -> {
                    try {
                        val intent = Intent()
                        intent.setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                        miuiAutoStartLauncher.launch(intent)
                    } catch (e: Exception) {
                        prefs.setMiuiAutostartCompleted()
                        currentStep = computeNextStep()
                    }
                }
                PermissionStep.ONEPLUS_AUTOSTART -> {
                    val launched = CallReliabilityUtils.launchAutoStartSettings(context)
                    if (!launched) {
                        try {
                            onePlusAutoStartLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        } catch (e: Exception) {
                            prefs.setOemAutostartCompleted()
                            currentStep = computeNextStep()
                        }
                    }
                }
                PermissionStep.DONE -> {}
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                val title = if (isGenericExtraStep) strPermissionMiuiTitle else strPermissionsRequiredTitle
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    modifier = Modifier.clickable(onClick = onPermissionActionClick)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val description = when (currentStep) {
                    PermissionStep.OVERLAY -> strPermissionOverlayDesc
                    PermissionStep.MIUI_PERMISSIONS -> strPermissionMiuiDesc
                    PermissionStep.MIUI_AUTOSTART -> strPermissionMiuiDesc
                    PermissionStep.ONEPLUS_AUTOSTART -> strPermissionMiuiDesc
                    PermissionStep.FULL_SCREEN_INTENT -> strPermissionMiuiDesc
                    else -> ""
                }

                Text(
                    text = description,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    fontStyle = if (isGenericExtraStep) FontStyle.Italic else FontStyle.Normal,
                    color = descColor,
                    modifier = Modifier.clickable(onClick = onPermissionActionClick)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isGenericExtraStep) {
                        Image(
                            painter = painterResource(
                                id = if (currentStep == PermissionStep.MIUI_PERMISSIONS) {
                                    R.drawable.display_pop_up_main
                                } else {
                                    R.drawable.auto_start_main
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .offset(y = (-40).dp)
                                .clickable(onClick = onPermissionActionClick)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.allow_display_over_other_apps_main),
                            contentDescription = null,
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .offset(y = (-40).dp)
                                .clickable(onClick = onPermissionActionClick)
                        )
                    }
                }

                val buttonText = when (currentStep) {
                    PermissionStep.OVERLAY -> strActionGoToSettings
                    PermissionStep.MIUI_PERMISSIONS -> strActionGrantPermission
                    PermissionStep.MIUI_AUTOSTART -> strActionGrantAutostartPermission
                    PermissionStep.ONEPLUS_AUTOSTART -> strActionGrantAutostartPermission
                    PermissionStep.FULL_SCREEN_INTENT -> strActionGrantPermission
                    else -> ""
                }

                Button(
                    onClick = onPermissionActionClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text(
                        text = buttonText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
