package com.tripcompanion.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tripcompanion.app.data.local.ImageStorageHelper
import com.tripcompanion.app.feature.photo.PlannedPhotoViewModel
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.Eyebrow
import com.tripcompanion.app.ui.theme.AppThemeExtended
import kotlinx.coroutines.launch

/**
 * Add or edit a planned photo (§15).
 *
 * The image is the whole point, so it takes the top of the screen at full width
 * and everything else is one optional line of text. There is no field here for
 * pose, framing, lens or notes — that shape turned a visual reference board into
 * a photography assignment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedPhotoEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: PlannedPhotoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val metrics = AppThemeExtended.metrics
    val extColors = AppThemeExtended.colors
    var isCopyingImage by remember { mutableStateOf(false) }
    var copyFailed by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isCopyingImage = true
                copyFailed = false
                try {
                    // Copy into app-private storage so the reference survives the
                    // picker's temporary permission grant (§16).
                    val permanentPath = ImageStorageHelper.saveImageToInternalStorage(context, uri)
                    viewModel.updateReferenceUri(permanentPath)
                } catch (_: Exception) {
                    copyFailed = true
                } finally {
                    isCopyingImage = false
                }
            }
        }
    }

    fun pickImage() = photoPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit photo" else "Add photo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(metrics.screenPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(metrics.cardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(metrics.borderWidth, MaterialTheme.colorScheme.outline, metrics.cardShape)
                    .clickable(enabled = !isCopyingImage) { pickImage() },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCopyingImage -> CircularProgressIndicator(color = extColors.accent)

                    state.referenceImageUri.isNotBlank() -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(state.referenceImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Selected photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = metrics.chipShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(metrics.borderWidth, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Replace", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = extColors.accent,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Choose the photo you want to recreate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (copyFailed) {
                Text(
                    "That image could not be copied into the app. Pick it again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))

            Eyebrow("Label — optional")
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                placeholder = { Text("Palace doorway") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = metrics.controlShape
            )

            Spacer(Modifier.height(metrics.rowGap))

            PrimaryButton(
                text = if (state.isEditing) "Save photo" else "Add photo",
                onClick = viewModel::save,
                enabled = state.canSave,
                busy = state.isSaving
            )
        }
    }
}
