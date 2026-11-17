package dev.dokkaebi.tablet

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import dev.dokkaebi.tablet.ui.Hud

/**
 * Places real phone calls for REAL-mode targets.
 *
 * - With CALL_PHONE granted, the call is placed directly (ACTION_CALL).
 * - If the user declines the permission, it falls back to opening the dialer with the
 *   number filled in (ACTION_DIAL), which needs no permission — the user presses call.
 * - An optional confirmation dialog guards against accidental taps.
 */
class RealCaller internal constructor(private val request: (Target) -> Unit) {
    fun call(t: Target) = request(t)
}

@Composable
fun rememberRealCaller(vm: TabletViewModel): RealCaller {
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<Target?>(null) }
    var awaitingPermission by remember { mutableStateOf<Target?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        awaitingPermission?.let { t -> launchCall(context, vm, t, direct = granted) }
        awaitingPermission = null
    }

    fun proceed(t: Target) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCall(context, vm, t, direct = true)
        else { awaitingPermission = t; permission.launch(Manifest.permission.CALL_PHONE) }
    }

    confirm?.let { t ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Hud.Panel,
            title = { Text("PLACE REAL CALL?", style = Hud.mono(16, FontWeight.Black, Hud.Hot, 2f)) },
            text = {
                Text(
                    "This will actually call ${t.name}\n${t.number}\n\nNormal carrier charges apply.",
                    style = Hud.mono(13, color = Hud.Text, spacing = 0f),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; proceed(t) }) {
                    Text("CALL", style = Hud.mono(13, FontWeight.Bold, Hud.Hot, 2f))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text("CANCEL", style = Hud.mono(13, FontWeight.Bold, Hud.TextDim, 2f))
                }
            },
        )
    }

    return remember(vm) {
        RealCaller { t ->
            if (t.number.isNullOrBlank()) return@RealCaller
            if (vm.confirmRealCalls) confirm = t else proceed(t)
        }
    }
}

private fun launchCall(context: Context, vm: TabletViewModel, t: Target, direct: Boolean) {
    val uri = Uri.fromParts("tel", t.number, null)
    val intent = Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
        vm.markRealCall(t.id)
        if (!direct) vm.log("CALL PERMISSION DENIED — OPENED DIALER INSTEAD", 2)
    } catch (e: ActivityNotFoundException) {
        vm.log("NO DIALER ON THIS DEVICE", 2)
    } catch (e: SecurityException) {
        vm.log("CALL BLOCKED BY SYSTEM: ${e.message}", 2)
    }
}

/**
 * Opens the system contact picker filtered to phone numbers. The picker grants
 * one-off read access to the chosen row, so no READ_CONTACTS permission is needed.
 */
class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

/** Reads (display name, number) from a row returned by [PickPhoneContact]. */
fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else null }
}.getOrNull()
