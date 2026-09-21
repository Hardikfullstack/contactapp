package com.phone.contact.call.dialer.ui.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.ClickableText
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
import com.phone.contact.call.dialer.R
import com.phone.contact.call.dialer.ui.components.animatedPulse
import com.phone.contact.call.dialer.ui.theme.LocalIsDarkTheme
import com.phone.contact.call.dialer.ui.theme.PrimaryGreen
import androidx.compose.foundation.Image
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher

@Composable
fun PermissionScreen(
    onContinue: () -> Unit,
    onPrivacyPolicyClick: () -> Unit = {}
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
    var permissionGroupIndex by remember { mutableIntStateOf(0) }

    // The background reliability chain (overlay, MIUI autostart, etc) is handled by
    // AdvancedPermissionScreen.kt after this screen. The "set as default dialer" role request
    // no longer happens during onboarding at all — it's now prompted once on the Home screen
    // (see RecentsScreen.kt) right after onboarding finishes.
    fun proceedAfterPermissions() {
        onContinue()
    }

    // lateinit (not val) because the callback below needs to launch the NEXT group by calling
    // this same launcher again — a val's initializer lambda can't reference the val itself.
    lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Always chain into the next group regardless of whether THIS group was granted or
        // denied — Notification, then Call/Phone State, both get their own real chance to show,
        // matching the intended sequential order. Once every group has been asked (whatever the
        // outcome), just move the user forward — AdvancedPermissionScreen (Display over other
        // apps, next) still gets its own chance regardless of what happened with these.
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
            proceedAfterPermissions()
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
                painter = painterResource(id = R.drawable.permission_main),
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
            val privacyTextColor = if (LocalIsDarkTheme.current) MaterialTheme.colorScheme.onSurface else Color(0xFF020202)
            val privacyText = buildAnnotatedString {
                // Trailing space in the XML string resource gets trimmed by the resource compiler
                // at build time (unquoted strings lose leading/trailing whitespace), so the space
                // before "Privacy Policy" is added explicitly here instead.
                append(privacyPolicyPrefix.trimEnd() + " ")
                pushStringAnnotation(tag = "privacy_policy", annotation = "privacy_policy")
                withStyle(
                    style = SpanStyle(
                        color = PrimaryGreen,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(privacyPolicyLink)
                }
                pop()
                append(".")
            }

            ClickableText(
                text = privacyText,
                modifier = Modifier.fillMaxWidth(),
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = privacyTextColor,
                    textAlign = TextAlign.Center
                ),
                onClick = { offset ->
                    privacyText.getStringAnnotations("privacy_policy", offset, offset)
                        .firstOrNull()?.let { onPrivacyPolicyClick() }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
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
