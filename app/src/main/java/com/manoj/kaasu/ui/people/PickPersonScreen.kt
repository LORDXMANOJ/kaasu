package com.manoj.kaasu.ui.people

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import com.manoj.kaasu.ui.common.TextInputDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.manoj.kaasu.data.db.Contact
import com.manoj.kaasu.data.repo.ContactRepository
import com.manoj.kaasu.data.upi.UpiUri
import com.manoj.kaasu.ui.components.Avatar
import com.manoj.kaasu.ui.components.BackTopBar
import com.manoj.kaasu.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A contact from the phone book, reduced to a name and a 10-digit mobile number. */
data class PhoneContact(val name: String, val number: String)

@HiltViewModel
class PickPersonViewModel @Inject constructor(contacts: ContactRepository) : ViewModel() {
    val saved: StateFlow<List<Contact>> = contacts.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Reads phone contacts on this device only (Kaasu has no internet). Mobile numbers only, one row per number. */
private suspend fun loadPhoneContacts(context: Context): List<PhoneContact> = withContext(Dispatchers.IO) {
    val out = LinkedHashMap<String, PhoneContact>()
    val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
    runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0).orEmpty()
                val vpa = UpiUri.upiNumberVpa(c.getString(1)) ?: continue
                val number = vpa.substringBefore('@')
                if (number !in out) out[number] = PhoneContact(name.ifBlank { number }, number)
            }
        }
    }
    out.values.toList()
}

private fun personLink(vpa: String, name: String?): String =
    "upi://pay?pa=$vpa" + (UpiUri.safePayeeName(name)?.takeIf { it != vpa.substringBefore('@') }?.let { "&pn=" + Uri.encode(it) } ?: "")

/** Number-based UPI IDs depend on the app the person signed up with. Kaasu can't look this up, but the UPI app shows the real name before the PIN. */
private val NUMBER_HANDLES = listOf(
    Triple("PhonePe", "ybl", "Most common"),
    Triple("Paytm", "ptyes", null),
    Triple("super.money", "superyes", null),
    Triple("PhonePe (older)", "axl", null),
    Triple("PhonePe (ICICI)", "ibl", null),
    Triple("Paytm (older)", "paytm", null),
    Triple("BHIM / UPI number", "upi", null),
)

/** Who we're about to pay by mobile number; the handle is chosen in a dialog. */
private data class NumberTarget(val number: String, val name: String?)

private fun matches(query: String, vararg fields: String?): Boolean {
    if (query.isBlank()) return true
    val digits = query.filter { it.isDigit() }
    return fields.any { f ->
        f != null && (f.contains(query.trim(), ignoreCase = true) || (digits.length >= 3 && f.filter { it.isDigit() }.contains(digits)))
    }
}

@Composable
fun PickPersonScreen(navController: NavHostController, vm: PickPersonViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val saved by vm.saved.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var numberMode by rememberSaveable { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var phoneContacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    val focus = remember { FocusRequester() }
    var target by remember { mutableStateOf<NumberTarget?>(null) }
    var typingId by remember { mutableStateOf<NumberTarget?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    LaunchedEffect(hasPermission) { if (hasPermission) phoneContacts = loadPhoneContacts(context) }
    LaunchedEffect(numberMode) { if (numberMode) focus.requestFocus() }

    val typedVpa = UpiUri.upiNumberVpa(query)
    val savedShown = saved.filter { matches(query, it.name, it.vpa, it.phone) }
    val savedNumbers = saved.mapNotNull { UpiUri.upiNumberVpa(it.phone)?.substringBefore('@') }.toSet()
    val phoneShown = phoneContacts.filter { it.number !in savedNumbers && matches(query, it.name, it.number) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        BackTopBar(title = "Pay a person", onBack = { navController.popBackStack() })
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = { Text("Search name or mobile number") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = if (numberMode) KeyboardType.Phone else KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focus),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (typedVpa != null) {
                item(key = "typed") {
                    PickRow(
                        title = "Pay ${typedVpa.substringBefore('@')}",
                        subtitle = "Pick their UPI app next · check their name before the PIN",
                        icon = Icons.Rounded.Dialpad,
                    ) { target = NumberTarget(typedVpa.substringBefore('@'), null) }
                }
            } else {
                item(key = "new") {
                    PickRow(title = "New mobile number", subtitle = null, icon = Icons.Rounded.Dialpad) {
                        numberMode = true
                        focus.requestFocus()
                    }
                }
            }
            if (savedShown.isNotEmpty()) {
                item(key = "savedHeader") { Header("Saved in Kaasu") }
                items(savedShown, key = { "s${it.id}" }) { c ->
                    PickRow(title = c.name, subtitle = c.vpa ?: c.phone ?: "No UPI ID yet", avatar = c.name) {
                        navController.navigate(Routes.pay(contactId = c.id))
                    }
                }
            }
            if (!hasPermission) {
                item(key = "perm") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("Show my phone contacts", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "Read only on this phone to fill the list. Kaasu has no internet, so contacts never leave it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            TextButton(onClick = { askPermission.launch(Manifest.permission.READ_CONTACTS) }) { Text("Allow contacts") }
                        }
                    }
                }
            } else if (phoneShown.isNotEmpty()) {
                item(key = "phoneHeader") { Header("All contacts") }
                items(phoneShown, key = { "p${it.number}" }) { pc ->
                    PickRow(title = pc.name, subtitle = "+91 ${pc.number}", avatar = pc.name) {
                        target = NumberTarget(pc.number, pc.name)
                    }
                }
            }
        }
    }
    target?.let { t ->
        PickHandleDialog(
            target = t,
            onPick = { vpa ->
                target = null
                navController.navigate(Routes.pay(raw = personLink(vpa, t.name)))
            },
            onTypeId = {
                target = null
                typingId = t
            },
            onScan = {
                target = null
                navController.navigate(Routes.scan())
            },
            onDismiss = { target = null },
        )
    }
    typingId?.let { t ->
        TextInputDialog(
            title = "Their UPI ID",
            initial = "",
            label = "UPI ID",
            placeholder = "name@okaxis",
            keyboardType = KeyboardType.Email,
            allowEmpty = false,
            onConfirm = { id ->
                if (UpiUri.isValidVpa(id.trim())) {
                    typingId = null
                    navController.navigate(Routes.pay(raw = personLink(id.trim(), t.name)))
                } else {
                    Toast.makeText(context, "Looks like name@bank, e.g. sanjith@okaxis", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { typingId = null },
        )
    }
}

@Composable
private fun PickHandleDialog(target: NumberTarget, onPick: (String) -> Unit, onTypeId: () -> Unit, onScan: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which app does ${target.name?.takeIf { it != target.number } ?: target.number} use?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Your UPI app will show their real name before the PIN. If it says invalid, come back and pick another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                NUMBER_HANDLES.forEach { (app, handle, hint) ->
                    PickRow(title = app, subtitle = "${target.number}@$handle" + (hint?.let { " · $it" } ?: "")) { onPick("${target.number}@$handle") }
                }
                PickRow(title = "I have their UPI ID", subtitle = "GPay users: name@okaxis, name@oksbi…", icon = Icons.Rounded.Edit, onClick = onTypeId)
                PickRow(title = "Scan their QR", subtitle = "Surest. Saves it for next time", icon = Icons.Rounded.QrCodeScanner, onClick = onScan)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PickRow(title: String, subtitle: String?, icon: ImageVector? = null, avatar: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            Avatar(avatar)
        } else if (icon != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(10.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (icon != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
