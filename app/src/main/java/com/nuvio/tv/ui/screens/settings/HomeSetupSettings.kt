package com.nuvio.tv.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.collections.HomeSetupService
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.HomeSetupConfigServer
import com.nuvio.tv.ui.screens.addon.QrCodeOverlay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class HomeSetupShareState(val qrCode: Bitmap? = null, val url: String? = null)

@HiltViewModel
class HomeSetupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val service: HomeSetupService,
) : ViewModel() {
    private var server: HomeSetupConfigServer? = null
    private val _share = MutableStateFlow<HomeSetupShareState?>(null)
    val share: StateFlow<HomeSetupShareState?> = _share.asStateFlow()

    /** Starts the phone page and shows its QR code; a toast says why when it can't. */
    fun startSharing() {
        val ip = DeviceIpAddress.get(context) ?: return toast(context.getString(R.string.error_network_required))
        stopServer()
        server = HomeSetupConfigServer.startOnAvailablePort(service, onImported = {
            viewModelScope.launch { toast(context.getString(R.string.home_setup_imported_from_phone)) }
        }) ?: return toast(context.getString(R.string.error_server_ports_unavailable))
        val url = "http://$ip:${server!!.listeningPort}"
        _share.value = HomeSetupShareState(QrCodeGenerator.generate(url, 512), url)
    }

    fun stopSharing() {
        stopServer()
        _share.value = null
    }

    fun importFromUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            val message = runCatching { withContext(Dispatchers.IO) { service.importFromUrl(url) } }.fold(
                onSuccess = { context.getString(R.string.home_setup_imported, it.collections) },
                onFailure = { context.getString(R.string.home_setup_import_failed) }
            )
            toast(message)
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    override fun onCleared() {
        stopServer()
    }
}

/** Sharing the home setup with a phone, and importing one from a link. */
@Composable
internal fun HomeSetupRows(viewModel: HomeSetupViewModel = hiltViewModel()) {
    val share by viewModel.share.collectAsState()
    var showLinkDialog by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.home_setup_share),
        subtitle = stringResource(R.string.home_setup_share_subtitle),
        onClick = { viewModel.startSharing() },
        leadingIcon = Icons.Default.QrCode2
    )
    SettingsActionRow(
        title = stringResource(R.string.home_setup_import_link),
        subtitle = stringResource(R.string.home_setup_import_link_subtitle),
        onClick = { showLinkDialog = true },
        leadingIcon = Icons.Default.Link
    )
    share?.let { state ->
        Dialog(onDismissRequest = { viewModel.stopSharing() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            QrCodeOverlay(
                qrBitmap = state.qrCode,
                serverUrl = state.url,
                instruction = stringResource(R.string.home_setup_share_instruction),
                onClose = { viewModel.stopSharing() },
                qrSize = 200.dp
            )
        }
    }
    if (showLinkDialog) {
        TranslationTextDialog(
            title = stringResource(R.string.home_setup_import_link),
            subtitle = stringResource(R.string.home_setup_import_link_dialog_subtitle),
            initialValue = "",
            placeholder = "https://example.com/nuvio-home-setup.json",
            onClear = null,
            onSave = { url -> viewModel.importFromUrl(url) },
            onDismiss = { showLinkDialog = false }
        )
    }
}
