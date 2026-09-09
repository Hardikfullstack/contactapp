package com.example.contactapp.ui.features.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.R
import com.example.contactapp.ui.components.animatedPulse
import com.example.contactapp.ui.theme.LocalIsDarkTheme
import com.example.contactapp.ui.theme.PrimaryGreen
import androidx.compose.foundation.Image
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher

@Composable
fun PermissionScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    // Requested as separate sequential groups (not one flat array) so the system dialogs are
    // guaranteed to appear in THIS order — Notification, then Phone/Calls — matching what was
    // asked for. A single RequestMultiplePermissions() call with a flat array does not reliably
    // preserve array order across OEMs/Android versions.
    //
    // Contacts/Call Log are intentionally NOT requested here anymore — they're asked for lazily
    // when the user first opens a screen that actually needs them (matching the reference
    // competitor flow), instead of upfront during onboarding.
    val permissionGroups = remember {
        buildList<List<String>> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(listOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            add(listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE))
        }
    }
    val permissionsToRequest = remember(permissionGroups) { permissionGroups.flatten() }
    var permissionGroupIndex by remember { mutableIntStateOf(0) }

    // The background reliability chain (overlay, MIUI autostart, etc) is handled by
    // AdvancedPermissionScreen.kt after this screen. The "set as default dialer" role request
    // no longer happens during onboarding at all — it's now prompted once on the Home screen
    // (see RecentsScreen.kt) right after onboarding finishes.
    fun proceedAfterPermissions() {
        onContinue()
    }

    var showSettingsDialog by remember { mutableStateOf(false) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) proceedAfterPermissions()
    }

    // lateinit (not val) because the callback below needs to launch the NEXT group by calling
    // this same launcher again — a val's initializer lambda can't reference the val itself.
    lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        permissionGroupIndex++
        if (permissionGroupIndex < permissionGroups.size) {
            val nextGroup = permissionGroups[permissionGroupIndex]
            // Posted (not launched synchronously right here) — launching a new
            // ActivityResultLauncher from inside another launcher's own callback in the same
            // frame can get silently dropped/misordered by Android; deferring to the next
            // message-loop tick via Handler.post avoids that without an arbitrary sleep.
            Handler(Looper.getMainLooper()).post {
                permissionLauncher.launch(nextGroup.toTypedArray())
            }
        } else {
            val allGranted = permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                proceedAfterPermissions()
            } else {
                // If the system can no longer show a rationale for any still-denied permission,
                // the user has denied it (usually the second time) with "Don't allow" — the
                // request dialog won't reappear, so send them to app settings instead.
                val activity = context as? Activity
                val canAskAgain = activity != null && permissionsToRequest.any { permission ->
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                }
                if (!canAskAgain) {
                    showSettingsDialog = true
                }
            }
        }
    }

    fun launchPermissions() {
        permissionGroupIndex = 0
        permissionLauncher.launch(permissionGroups[0].toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.background else Color.White)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(15.dp))

            // Illustration — full width, breaking out of the horizontal padding every other
            // element on this screen uses (see the padded Column right below).
            Image(
                painter = painterResource(id = R.drawable.permission),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(15.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.allow_permission),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryGreen
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.permission_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                PermissionItem(
                    icon = Icons.Outlined.NotificationsNone,
                    title = stringResource(R.string.smart_notifications),
                    description = stringResource(R.string.notifications_description),
                    onClick = { launchPermissions() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PermissionItem(
                    icon = Icons.Default.Call,
                    title = stringResource(R.string.enable_call_access),
                    description = stringResource(R.string.call_access_description),
                    onClick = { launchPermissions() }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { launchPermissions() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .animatedPulse(PrimaryGreen),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryGreen
                )
            ) {
                Text(
                    text = stringResource(R.string.agree_continue),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val privacyPolicyPrefix = stringResource(R.string.privacy_policy_prefix)
            val privacyPolicyLink = stringResource(R.string.privacy_policy)
            val privacyText = buildAnnotatedString {
                // Trailing space in the XML string resource gets trimmed by the resource compiler
                // at build time (unquoted strings lose leading/trailing whitespace), so the space
                // before "Privacy Policy" is added explicitly here instead.
                append(privacyPolicyPrefix.trimEnd() + " ")
                withStyle(
                    style = SpanStyle(
                        color = PrimaryGreen,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(privacyPolicyLink)
                }
                append(".")
            }

            Text(
                text = privacyText,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text(stringResource(R.string.permission_permanently_denied_title)) },
            text = { Text(stringResource(R.string.permission_permanently_denied_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showSettingsDialog = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    settingsLauncher.launch(intent)
                }) {
                    Text(stringResource(R.string.open_settings), color = PrimaryGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.outlineVariant else Color(0xFFEEEEEE)
        ),
        color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.surface else Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF656565),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
