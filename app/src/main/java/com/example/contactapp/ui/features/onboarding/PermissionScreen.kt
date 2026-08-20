package com.example.contactapp.ui.features.onboarding

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contactapp.R
import com.example.contactapp.ui.theme.PrimaryGreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.ui.draw.clip
import com.example.contactapp.util.CallReliabilityUtils

@Composable
fun PermissionScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val permissionsToRequest = mutableListOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        // Without this, AccountManager.accounts can't see the user's Google account, so every
        // contact this app creates falls back to a local-only (non-syncable) account — it can
        // never receive data like a profile photo from Google no matter how often it's synced.
        Manifest.permission.GET_ACCOUNTS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Being the default dialer only makes Telecom offer calls to this app — OEM battery
    // managers can still kill it in the background before an incoming call arrives, so
    // proactively ask for the battery-optimization exemption once the role is granted.
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        onContinue()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        if (!CallReliabilityUtils.isIgnoringBatteryOptimizations(context)) {
            try {
                batteryOptimizationLauncher.launch(CallReliabilityUtils.batteryOptimizationIntent(context))
            } catch (e: Exception) {
                onContinue()
            }
        } else {
            onContinue()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // After general permissions, request Role
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else if (!CallReliabilityUtils.isIgnoringBatteryOptimizations(context)) {
                try {
                    batteryOptimizationLauncher.launch(CallReliabilityUtils.batteryOptimizationIntent(context))
                } catch (e: Exception) {
                    onContinue()
                }
            } else {
                onContinue()
            }
        } else {
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Illustration
        Image(
            painter = painterResource(id = R.drawable.permission),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
//                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.allow_permission),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PrimaryGreen
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Note: the "set as default dialer" request still happens automatically after
        // permissions are granted (see permissionLauncher below) even without a card for it here.
        PermissionItem(
            icon = Icons.Outlined.NotificationsNone,
            title = stringResource(R.string.smart_notifications),
            description = stringResource(R.string.notifications_description)
        )

        Spacer(modifier = Modifier.height(14.dp))

        PermissionItem(
            icon = Icons.Default.Call,
            title = stringResource(R.string.enable_call_access),
            description = stringResource(R.string.call_access_description)
        )

        Spacer(modifier = Modifier.height(14.dp))

        PermissionItem(
            icon = Icons.Default.Smartphone,
            title = stringResource(R.string.set_as_default_title),
            description = stringResource(R.string.set_as_default_desc)
        )
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryGreen
            )
        ) {
            Text(
                text = stringResource(R.string.agree_continue),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val privacyText = buildAnnotatedString {
            append("By continuing, you agree to our ")
            withStyle(
                style = SpanStyle(
                    color = PrimaryGreen,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append("Privacy Policy")
            }
            append(".")
        }

        Text(
            text = privacyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE)),
        color = Color.White,
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
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
