package mw.phoneguard.app.ui.contacts

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mw.phoneguard.app.AppScreen
import mw.phoneguard.app.ui.theme.GuardTeal
import mw.phoneguard.app.ui.theme.LineColor
import mw.phoneguard.app.ui.theme.PanelDark
import mw.phoneguard.app.ui.theme.TextFaint
import mw.phoneguard.app.ui.theme.TextMuted
import mw.phoneguard.app.ui.theme.TextPrimary
import mw.phoneguard.app.ui.theme.WarnAmber

@Composable
fun ContactsScreen(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit,
    viewModel: ContactsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                        val number = if (numberIndex >= 0) cursor.getString(numberIndex) else ""
                        if (name.isNotBlank() && number.isNotBlank()) {
                            viewModel.addContact(name, number)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "TRUSTED CONTACTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextFaint
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Emergency Contacts",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Up to 3 contacts (with **country code**) for SMS escalation alerts and SIM change notifications.",
                fontSize = 13.sp,
                color = TextMuted
            )
        }

        // List content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            if (state.contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No trusted contacts added yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Add up to 3 contacts to receive alert messages.",
                            fontSize = 12.sp,
                            color = TextFaint
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.contacts) { index, contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(PanelDark)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.first,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = contact.second,
                                    fontSize = 13.sp,
                                    color = TextMuted
                                )
                            }
                            TextButton(onClick = { viewModel.removeContact(index) }) {
                                Text("Remove", color = WarnAmber, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (state.contacts.size < 3) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GuardTeal)
                ) {
                    Text(
                        text = "Add Trusted Contact",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.background
                    )
                }
            }

            state.errorMessage?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(text = error, color = WarnAmber, fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))
        }

        // Bottom nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            mw.phoneguard.app.ui.activity.BottomNavItem("Status", currentScreen == AppScreen.Home) { onNavigate(AppScreen.Home) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Activity", currentScreen == AppScreen.Activity) { onNavigate(AppScreen.Activity) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Contacts", currentScreen == AppScreen.Contacts) { onNavigate(AppScreen.Contacts) }
            mw.phoneguard.app.ui.activity.BottomNavItem("Settings", currentScreen == AppScreen.Settings) { onNavigate(AppScreen.Settings) }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Trusted Contact", color = TextPrimary) },
            text = {
                Column {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            contactPickerLauncher.launch(intent)
                            showAddDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Pick from Phone Contacts", color = GuardTeal)
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Or enter manually:",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor,
                            focusedLabelColor = GuardTeal,
                            cursorColor = GuardTeal
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number (with country code)") },
                        placeholder = { Text("+265980096500") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GuardTeal,
                            unfocusedBorderColor = LineColor,
                            focusedLabelColor = GuardTeal,
                            cursorColor = GuardTeal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addContact(name, phone)
                        showAddDialog = false
                    }
                ) {
                    Text("Add", color = GuardTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelDark
        )
    }
}
