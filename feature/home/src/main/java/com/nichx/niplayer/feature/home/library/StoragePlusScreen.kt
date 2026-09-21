package com.nichx.niplayer.feature.home.library

import com.nichx.niplayer.feature.home.R
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import com.nichx.niplayer.designsystem.components.NiConfirmDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.nichx.niplayer.common.error.NiMessage
import com.nichx.niplayer.datastore.ThumbnailGenerationMode
import com.nichx.niplayer.datastore.ThumbnailSettings
import com.nichx.niplayer.designsystem.components.LocalAppMessageController
import com.nichx.niplayer.designsystem.components.NiDialogItem
import com.nichx.niplayer.designsystem.components.NiListItemDialog
import com.nichx.niplayer.designsystem.components.NiScaffold
import com.nichx.niplayer.designsystem.components.NiTopBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.designsystem.theme.NiExtraColors

@Composable
fun StoragePlusScreen(
    onBack: () -> Unit,
    viewModel: StoragePlusViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messageController = LocalAppMessageController.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showThumbnailModeDialog by remember { mutableStateOf(false) }
    var showThumbnailWriteBackDialog by remember { mutableStateOf(false) }
    val extraColors = NiExtraColors.current

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val name = queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
            viewModel.updateExternalUri(uri.toString())
            viewModel.ensureExternalDisplayName(name)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StoragePlusEvent.ShowError ->
                messageController.post(NiMessage.error(event.message))

                StoragePlusEvent.NavigateBack -> onBack()
                StoragePlusEvent.Saved -> Unit
            }
        }
    }

    NiScaffold(
        topBar = {
            NiTopBar(
                title = if (viewModel.isEditMode) stringResource(R.string.storage_plus_edit_title) else stringResource(R.string.storage_plus_add_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            StorageTypeBadge(type = state.mediaType, extraColors = extraColors)

            FormCard(
                title = stringResource(R.string.storage_plus_basic_info),
                icon = Icons.Filled.Info,
                iconBg = Color(0xFF2095F4),
            ) {
                FormTextField(
                    label = stringResource(R.string.storage_plus_display_name),
                    value = state.displayName,
                    onValueChange = viewModel::updateDisplayName,
                    placeholder = stringResource(R.string.storage_plus_default_name_hint),
                )
            }

            when (state.mediaType) {
                MediaType.WEBDAV_SERVER -> WebDavCards(state, viewModel, passwordVisible) {
                    passwordVisible = it
                }
                MediaType.SMB_SERVER -> SmbCards(state, viewModel, passwordVisible) {
                    passwordVisible = it
                }
                MediaType.EXTERNAL_STORAGE -> ExternalCard(state, extraColors) {
                    treeLauncher.launch(null)
                }
                MediaType.LOCAL_STORAGE,
                MediaType.OTHER_STORAGE,
                MediaType.QUICK_ACCESS -> Text(stringResource(R.string.storage_plus_unsupported_type))
            }

            state.testResult?.let { ok ->
                TestResultCard(ok = ok)
            }

            if (state.mediaType == MediaType.SMB_SERVER || state.mediaType == MediaType.WEBDAV_SERVER) {
                ThumbnailCard(
                    state = state,
                    onPickMode = { showThumbnailModeDialog = true },
                    onPickWriteBack = { showThumbnailWriteBackDialog = true },
                )
            }

            StorageFormActions(
                state = state,
                onTest = viewModel::testConnection,
                onSave = viewModel::save,
            )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }

    if (showDeleteDialog) {
        NiConfirmDialog(
            title = stringResource(R.string.storage_plus_delete_title),
            text = stringResource(R.string.storage_plus_delete_confirm, state.displayName.ifBlank { state.url }),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false },
            confirmText = stringResource(R.string.delete),
        )
    }

    if (showThumbnailModeDialog) {
        val globalMode = ThumbnailSettings.generationMode
        NiListItemDialog(
            title = stringResource(R.string.storage_plus_thumbnail_mode_title),
            onDismiss = { showThumbnailModeDialog = false },
            items = buildList {
                add(
                    NiDialogItem(
                        label = stringResource(R.string.thumbnail_follow_global, stringResource(globalMode.labelRes)),
                        isSelected = state.thumbnailMode == null,
                        onClick = {
                            viewModel.updateThumbnailMode(null)
                            showThumbnailModeDialog = false
                        },
                    ),
                )
                ThumbnailGenerationMode.entries.forEach { option ->
                    add(
                        NiDialogItem(
                            label = stringResource(option.labelRes),
                            isSelected = state.thumbnailMode == option,
                            onClick = {
                                viewModel.updateThumbnailMode(option)
                                showThumbnailModeDialog = false
                            },
                        ),
                    )
                }
            },
        )
    }

    if (showThumbnailWriteBackDialog) {
        val globalWb = if (ThumbnailSettings.saveInSameDir) {
            stringResource(R.string.thumbnail_writeback_enabled)
        } else {
            stringResource(R.string.thumbnail_writeback_disabled)
        }
        NiListItemDialog(
            title = stringResource(R.string.storage_plus_thumbnail_writeback_title),
            onDismiss = { showThumbnailWriteBackDialog = false },
            items = listOf(
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_follow_global, globalWb),
                    isSelected = state.thumbnailWriteBack == null,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(null)
                        showThumbnailWriteBackDialog = false
                    },
                ),
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_enabled),
                    isSelected = state.thumbnailWriteBack == true,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(true)
                        showThumbnailWriteBackDialog = false
                    },
                ),
                NiDialogItem(
                    label = stringResource(R.string.thumbnail_writeback_disabled),
                    isSelected = state.thumbnailWriteBack == false,
                    onClick = {
                        viewModel.updateThumbnailWriteBack(false)
                        showThumbnailWriteBackDialog = false
                    },
                ),
            ),
        )
    }
}
