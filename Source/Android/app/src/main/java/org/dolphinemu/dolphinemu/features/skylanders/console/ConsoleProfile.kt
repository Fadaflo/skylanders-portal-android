// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val ProfileInk = PortalPalette.Background
private val ProfileIce = PortalPalette.Focus
private val ProfileMuted = PortalPalette.Muted
private val ProfileShape = RoundedCornerShape(2.dp)

@Composable
internal fun ProfileAvatar(avatar: String, name: String?, size: Dp, selected: Boolean,
    onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val figure = consoleCatalog.firstOrNull { it.key == avatar } ?: consoleCatalog.first()
    Box(modifier.size(size).onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else if (selected) 2.dp else 1.dp,
            if (focused || selected) ProfileIce else Color.White.copy(alpha = 0.6f), CircleShape)
        .padding(3.dp).clip(CircleShape).background(Color(figure.color))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .semantics { contentDescription = "Profil ${name?.takeIf { it.isNotBlank() } ?: "öffnen"}" },
        contentAlignment = Alignment.Center) {
        AsyncImage("file:///android_asset/skylanders/images/${figure.artwork}",
            name ?: "Profilbild", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
internal fun ProfilePage(account: ConsoleAccountState, games: List<ConsoleGame>,
    cloudStatus: String, cloudBusy: Boolean, onCloudRefresh: () -> Unit,
    onBack: () -> Unit, onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit, onSignOut: () -> Unit,
    onUpdateName: (String) -> Unit, onUpdateAvatar: (String) -> Unit,
    onUpdatePassword: (String, String) -> Unit, onRequestReset: (String) -> Unit,
    onCompleteReset: (String, String, String) -> Unit) {
    val backFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("SKYLANDERS · PROFIL", color = ProfileIce, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(if (account.email == null) "Dein Profil." else "Willkommen zurück.",
                    color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            ProfileButton("Zurück", Icons.Outlined.ArrowBack, onBack,
                modifier = Modifier.focusRequester(backFocus))
        }
        if (account.email == null) {
            SignedOutProfile(account, onSignIn, onSignUp, onRequestReset, onCompleteReset)
        } else {
            SignedInProfile(account, games, cloudStatus, cloudBusy, onCloudRefresh,
                onSignOut, onUpdateName, onUpdateAvatar,
                onUpdatePassword)
        }
    }
}

@Composable
private fun SignedInProfile(account: ConsoleAccountState, games: List<ConsoleGame>,
    cloudStatus: String, cloudBusy: Boolean, onCloudRefresh: () -> Unit,
    onSignOut: () -> Unit, onUpdateName: (String) -> Unit, onUpdateAvatar: (String) -> Unit,
    onUpdatePassword: (String, String) -> Unit) {
    var editingName by remember { mutableStateOf(false) }
    var name by remember(account.email) { mutableStateOf(account.name.orEmpty()) }
    var editingPassword by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var nextPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(account.name) { name = account.name.orEmpty(); editingName = false }
    LaunchedEffect(account.message) {
        if (account.message == "Passwort geändert.") {
            currentPassword = ""; nextPassword = ""; repeatPassword = ""
            editingPassword = false
        }
    }
    ProfilePanel {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ProfileAvatar(account.avatar, account.name, 112.dp, selected = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(account.name?.takeIf { it.isNotBlank() } ?: "Skylander",
                    color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(account.email.orEmpty(), color = ProfileMuted, fontSize = 14.sp)
                Text(if (account.offline) "OFFLINE · LOKAL ANGEMELDET" else "PROFIL AKTIV",
                    color = ProfileIce, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp)
            }
        }
        Text("PROFILBILD", color = ProfileMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            consoleCatalog.forEach { game ->
                ProfileAvatar(game.key, game.title, 64.dp, selected = account.avatar == game.key,
                    onClick = { if (!account.busy) onUpdateAvatar(game.key) })
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 760.dp
        if (compact) Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PlayTimePanel(games)
            CloudPanel(cloudStatus, cloudBusy, onCloudRefresh)
            ProfileSettingsPanel(account, name, { name = it }, editingName, { editingName = it },
                editingPassword, { editingPassword = it }, currentPassword, { currentPassword = it },
                nextPassword, { nextPassword = it }, repeatPassword, { repeatPassword = it },
                localError, { localError = it }, onUpdateName, onUpdatePassword, onSignOut)
        } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.weight(1.1f)) { Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                PlayTimePanel(games)
                CloudPanel(cloudStatus, cloudBusy, onCloudRefresh)
            } }
            Box(Modifier.weight(0.9f)) {
                ProfileSettingsPanel(account, name, { name = it }, editingName, { editingName = it },
                    editingPassword, { editingPassword = it }, currentPassword, { currentPassword = it },
                    nextPassword, { nextPassword = it }, repeatPassword, { repeatPassword = it },
                    localError, { localError = it }, onUpdateName, onUpdatePassword, onSignOut)
            }
        }
    }
}

@Composable
private fun CloudPanel(status: String, busy: Boolean, onRefresh: () -> Unit) {
    ProfilePanel {
        Text("CLOUD-SPIELSTÄNDE", color = ProfileIce, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(status, color = Color.White, fontSize = 14.sp)
        Text("Figuren und Wii-Kampagnen gehören zu diesem Konto. Offline-Änderungen werden später abgeglichen.",
            color = ProfileMuted, fontSize = 12.sp)
        ProfileButton(if (busy) "Abgleich läuft …" else "Jetzt abgleichen",
            Icons.Outlined.CloudSync, onRefresh, enabled = !busy)
    }
}

@Composable
private fun PlayTimePanel(games: List<ConsoleGame>) {
    val installed = games.filter { it.path != null }
    val total = installed.sumOf { (it.playTimeMs ?: 0L).coerceAtLeast(0L) }
    ProfilePanel {
        Text("DEINE SPIELZEIT", color = ProfileIce, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(durationLabel(total), color = Color.White, fontSize = 32.sp,
            fontWeight = FontWeight.Black)
        Text("Dolphin auf diesem Gerät · alle lokalen Spiele",
            color = ProfileMuted, fontSize = 12.sp)
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        games.forEach { game ->
            Row(Modifier.fillMaxWidth().heightIn(min = 80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(106.dp, 64.dp).clip(ProfileShape)
                    .background(Color(game.color).copy(alpha = 0.3f))) {
                    AsyncImage(game.sceneArtwork ?: "file:///android_asset/skylanders/images/${game.artwork}",
                        game.displayTitle, modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(game.displayTitle, color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(if (game.path == null) "Nicht installiert" else durationLabel(game.playTimeMs ?: 0),
                        color = if (game.path == null) ProfileMuted else ProfileIce,
                        fontSize = 13.sp)
                }
            }
        }
    }
}

private fun durationLabel(ms: Long): String {
    if (ms <= 0) return "0 Min."
    val minutes = (ms + 59_999L) / 60_000L
    return if (minutes < 60) "$minutes Min."
        else "${minutes / 60} Std. ${minutes % 60} Min."
}

@Composable
private fun ProfileSettingsPanel(account: ConsoleAccountState, name: String,
    setName: (String) -> Unit, editingName: Boolean, setEditingName: (Boolean) -> Unit,
    editingPassword: Boolean, setEditingPassword: (Boolean) -> Unit,
    currentPassword: String, setCurrentPassword: (String) -> Unit,
    nextPassword: String, setNextPassword: (String) -> Unit,
    repeatPassword: String, setRepeatPassword: (String) -> Unit,
    localError: String?, setLocalError: (String?) -> Unit,
    onUpdateName: (String) -> Unit, onUpdatePassword: (String, String) -> Unit,
    onSignOut: () -> Unit) {
    ProfilePanel {
        Text("KONTO", color = ProfileIce, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("E-Mail-Adresse", color = ProfileMuted, fontSize = 12.sp)
        Text(account.email.orEmpty(), color = Color.White, fontSize = 16.sp)
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        if (editingName) {
            ProfileField(name, setName, "Nutzername")
            ProfileButton("Namen speichern", Icons.Outlined.Save,
                { onUpdateName(name.trim()) }, primary = true,
                enabled = !account.busy && name.trim().isNotEmpty() && name.trim().length <= 32)
        } else ProfileButton("Nutzernamen ändern", Icons.Outlined.Edit,
            { setEditingName(true) })
        if (editingPassword) {
            ProfileField(currentPassword, setCurrentPassword, "Aktuelles Passwort", password = true)
            ProfileField(nextPassword, setNextPassword, "Neues Passwort", password = true)
            ProfileField(repeatPassword, setRepeatPassword, "Neues Passwort wiederholen", password = true)
            ProfileButton("Passwort speichern", Icons.Outlined.LockReset, {
                setLocalError(when {
                    currentPassword.isBlank() -> "Gib dein aktuelles Passwort ein."
                    nextPassword.length < 8 -> "Das neue Passwort braucht mindestens 8 Zeichen."
                    nextPassword != repeatPassword -> "Die neuen Passwörter stimmen nicht überein."
                    currentPassword == nextPassword -> "Wähle ein anderes Passwort."
                    else -> null
                })
                if (currentPassword.isNotBlank() && nextPassword.length >= 8 &&
                    nextPassword == repeatPassword && currentPassword != nextPassword) {
                    onUpdatePassword(currentPassword, nextPassword)
                }
            }, primary = true, enabled = !account.busy)
        } else ProfileButton("Passwort ändern", Icons.Outlined.Lock,
            { setEditingPassword(true) })
        (localError ?: account.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        account.message?.let { Text(it, color = ProfileIce, fontSize = 13.sp) }
        HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
        ProfileButton("Abmelden", Icons.Outlined.Logout, onSignOut,
            enabled = !account.busy)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SignedOutProfile(account: ConsoleAccountState,
    onSignIn: (String, String) -> Unit, onSignUp: (String, String, String) -> Unit,
    onRequestReset: (String) -> Unit, onCompleteReset: (String, String, String) -> Unit) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    ProfilePanel(Modifier.widthIn(max = 650.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProfileAvatar("spyro", "Gast", 80.dp, selected = false)
            Column {
                Text("Noch nicht angemeldet", color = Color.White,
                    fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Deine lokalen Spiele und Figuren bleiben verfügbar.",
                    color = ProfileMuted, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProfileButton("Anmelden", Icons.Outlined.Login,
                { mode = "login"; localError = null }, primary = mode == "login")
            ProfileButton("Registrieren", Icons.Outlined.PersonAdd,
                { mode = "register"; localError = null }, primary = mode == "register")
        }
        if (mode == "register") ProfileField(name, { name = it }, "Nutzername (optional)")
        ProfileField(email, { email = it }, "E-Mail-Adresse", emailField = true)
        when (mode) {
            "login", "register" -> {
                ProfileField(password, { password = it }, "Passwort", password = true)
                if (mode == "register") Text("Mindestens 8 Zeichen. Danach bestätigst du deine E-Mail.",
                    color = ProfileMuted, fontSize = 12.sp)
                ProfileButton(if (mode == "register") "Konto erstellen" else "Anmelden",
                    if (mode == "register") Icons.Outlined.PersonAdd else Icons.Outlined.Login, {
                        localError = when {
                            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Gib eine gültige E-Mail-Adresse ein."
                            password.isEmpty() -> "Gib dein Passwort ein."
                            mode == "register" && password.length < 8 ->
                                "Das Passwort braucht mindestens 8 Zeichen."
                            else -> null
                        }
                        if (localError == null) {
                            if (mode == "register") onSignUp(email.trim(), password, name.trim())
                            else onSignIn(email.trim(), password)
                        }
                    }, primary = true, enabled = !account.busy)
                if (mode == "login") ProfileButton("Passwort vergessen?", Icons.Outlined.LockReset,
                    { mode = "reset"; localError = null })
            }
            "reset" -> {
                Text("Wir senden dir einen Wiederherstellungscode an diese Adresse.",
                    color = ProfileMuted, fontSize = 13.sp)
                ProfileButton("Code anfordern", Icons.Outlined.MarkEmailRead, {
                    localError = if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
                        "Gib eine gültige E-Mail-Adresse ein." else null
                    if (localError == null) onRequestReset(email.trim())
                }, primary = true, enabled = !account.busy)
                ProfileField(code, { code = it }, "Code aus der E-Mail")
                ProfileField(newPassword, { newPassword = it }, "Neues Passwort", password = true)
                ProfileButton("Passwort zurücksetzen", Icons.Outlined.LockReset, {
                    localError = if (code.isBlank() || newPassword.length < 8)
                        "Gib den Code und ein neues Passwort mit mindestens 8 Zeichen ein." else null
                    if (localError == null) onCompleteReset(email.trim(), code, newPassword)
                }, enabled = !account.busy)
            }
        }
        (localError ?: account.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        account.message?.let { Text(it, color = ProfileIce, fontSize = 13.sp) }
    }
}

@Composable
private fun ProfilePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PortalSurface(modifier.fillMaxWidth(), shadow = false, focusWholeSurface = false,
        shape = ProfileShape) {
        Box(Modifier.align(Alignment.TopStart).width(68.dp).height(3.dp)
            .background(ProfileIce))
        Column(Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
    }
}

@Composable
private fun ProfileField(value: String, onValue: (String) -> Unit, label: String,
    password: Boolean = false, emailField: Boolean = false) {
    OutlinedTextField(value, onValue, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = ProfileShape,
        visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password
                else if (emailField) KeyboardType.Email else KeyboardType.Text))
}

@Composable
private fun ProfileButton(label: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick, modifier.onFocusChanged { focused = it.isFocused }.heightIn(min = 48.dp),
        enabled = enabled, shape = ProfileShape,
        border = BorderStroke(if (focused) 3.dp else 1.dp,
            if (focused) ProfileIce else Color.White.copy(alpha = 0.16f)),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) Color.White
            else Color.White.copy(alpha = 0.08f),
            contentColor = if (primary) ProfileInk else Color.White)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
