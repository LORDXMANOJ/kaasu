package com.manoj.kaasu.ui.people

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.components.PrimaryButton
import com.manoj.kaasu.ui.navigation.Routes
import com.manoj.kaasu.ui.util.rememberLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PersonEditState(
    val isNew: Boolean = true,
    val name: String = "",
    val vpa: String = "",
    val phone: String = "",
    val error: String? = null,
    val saving: Boolean = false,
) {
    val vpaInvalid: Boolean get() = vpa.isNotBlank() && !UpiUri.isValidVpa(vpa)
    val canSave: Boolean get() = name.isNotBlank() && !vpaInvalid && !saving
}

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val contacts: ContactRepository,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(
        PersonEditState(
            isNew = id == 0L,
            vpa = savedState.get<String>("vpa").orEmpty(),
            name = savedState.get<String>("name").orEmpty(),
        ),
    )
    val state: StateFlow<PersonEditState> = _state.asStateFlow()
    private val _saved = Channel<Long>(Channel.CONFLATED)
    val saved: Flow<Long> = _saved.receiveAsFlow()

    init {
        if (id != 0L) {
            viewModelScope.launch {
                contacts.getById(id)?.let { c ->
                    _state.update { it.copy(name = c.name, vpa = c.vpa.orEmpty(), phone = c.phone.orEmpty()) }
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value.take(40), error = null) }
    fun onVpa(value: String) = _state.update { it.copy(vpa = value.trim().take(80), error = null) }
    fun onPhone(value: String) = _state.update { it.copy(phone = value.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16), error = null) }

    fun onScanned(vpa: String, name: String?) = _state.update {
        it.copy(vpa = vpa, name = it.name.ifBlank { name.orEmpty() }, error = null)
    }

    fun onContactPicked(name: String?, phone: String?) = _state.update {
        it.copy(name = it.name.ifBlank { name.orEmpty() }, phone = phone ?: it.phone, error = null)
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val vpa = s.vpa.trim().ifEmpty { null }
            val existing = vpa?.let { contacts.findByVpa(it) }
            if (existing != null && existing.id != id && !existing.archived) {
                _state.update { it.copy(saving = false, error = "${existing.name} already has this UPI ID") }
                return@launch
            }
            val savedId = if (existing != null && existing.archived && id == 0L) {
                contacts.save(existing.id, s.name, vpa, s.phone)
            } else {
                contacts.save(id, s.name, vpa, s.phone)
            }
            _saved.send(savedId)
        }
    }
}

@Composable
fun PersonEditScreen(navController: NavHostController, vm: PersonEditViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lockManager = rememberLockManager()
    val entry = navController.currentBackStackEntry
    val scannedVpa = entry?.savedStateHandle?.getStateFlow<String?>(Routes.RESULT_SCANNED_VPA, null)?.collectAsState()

    LaunchedEffect(scannedVpa?.value) {
        val vpa = scannedVpa?.value ?: return@LaunchedEffect
        vm.onScanned(vpa, entry.savedStateHandle.get<String>(Routes.RESULT_SCANNED_NAME))
        entry.savedStateHandle.remove<String>(Routes.RESULT_SCANNED_VPA)
        entry.savedStateHandle.remove<String>(Routes.RESULT_SCANNED_NAME)
    }
    LaunchedEffect(Unit) {
        vm.saved.collect { id ->
            navController.popBackStack()
            if (state.isNew) navController.navigate(Routes.person(id))
        }
    }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) vm.onContactPicked(cursor.getString(0), cursor.getString(1))
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        BackTopBar(title = if (state.isNew) "Add person" else "Edit person", onBack = { navController.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.vpa,
                onValueChange = vm::onVpa,
                label = { Text("UPI ID") },
                placeholder = { Text("name@okaxis") },
                isError = state.vpaInvalid,
                supportingText = {
                    Text(if (state.vpaInvalid) "Looks like name@bank" else "Scan their GPay / PhonePe QR to fill this exactly")
                },
                trailingIcon = {
                    IconButton(onClick = { navController.navigate(Routes.scan(Routes.SCAN_MODE_PERSON)) }) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan their QR")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = vm::onPhone,
                label = { Text("Mobile number") },
                supportingText = {
                    val upiNumber = UpiUri.upiNumberVpa(state.phone)
                    Text(
                        if (state.vpa.isBlank() && upiNumber != null) {
                            val n = upiNumber.substringBefore('@')
                            "No UPI ID? PhonePe users: $n@ybl · Paytm: $n@ptyes · super.money: $n@superyes"
                        } else {
                            "For WhatsApp payback reminders, or to pay them by number"
                        },
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        lockManager.markExternalLaunch()
                        runCatching {
                            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }
                    }) {
                        Icon(Icons.Rounded.Contacts, contentDescription = "Pick from contacts")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Scanning their QR is the surest. Paying by mobile number works when they've linked it to UPI. Your UPI app shows their real bank name before you enter the PIN, so check it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.padding(4.dp))
        }
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(20.dp),
        ) {
            PrimaryButton(text = "Save", enabled = state.canSave, loading = state.saving, onClick = vm::save)
        }
    }
}
