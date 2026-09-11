package com.example.contactapp.ui.features.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.contactapp.R
import com.example.contactapp.ads.NativeOrBannerAdView
import com.example.contactapp.domain.model.Contact
import com.example.contactapp.util.getAvatarColor
import com.example.contactapp.viewmodel.AppConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBack: () -> Unit,
    viewModel: BlockedNumbersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingUnblock by remember { mutableStateOf<Contact?>(null) }

    val context = LocalContext.current
    val appConfigViewModel: AppConfigViewModel = androidx.lifecycle.viewmodel.compose.viewModel(context as ComponentActivity)
    val adConfig by appConfigViewModel.appResponse.collectAsState()
    val bannerAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.banner_7_on_off == "on") {
            result.banner_7?.takeIf { it.isNotBlank() }
        } else null
    }
    // native_12 is unused elsewhere — tried first here (see NativeOrBannerAdView), falling back to
    // banner_7 above only if it fails to load.
    val nativeAdUnitId = adConfig?.result?.let { result ->
        if (result.google_ads_on_off == "on" && result.native_12_on_off == "on") {
            result.native_12?.takeIf { it.isNotBlank() }
        } else null
    }

    Scaffold(
        bottomBar = {
            if (nativeAdUnitId != null || bannerAdUnitId != null) {
                NativeOrBannerAdView(nativeAdUnitId = nativeAdUnitId, bannerAdUnitId = bannerAdUnitId)
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_numbers), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 2.dp).size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.blockedContacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_blocked_numbers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.blockedContacts, key = { it.number }) { contact ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(getAvatarColor(contact.name)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (contact.name != contact.number) {
                                    Text(
                                        text = contact.number,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            IconButton(onClick = { pendingUnblock = contact }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unblock",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val contactToUnblock = pendingUnblock
    if (contactToUnblock != null) {
        AlertDialog(
            onDismissRequest = { pendingUnblock = null },
            title = {
                Text(text = stringResource(R.string.unblock_number), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = stringResource(R.string.unblock_confirmation))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unblockNumber(contactToUnblock.number)
                    pendingUnblock = null
                }) {
                    Text(
                        text = stringResource(R.string.unblock).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnblock = null }) {
                    Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}
