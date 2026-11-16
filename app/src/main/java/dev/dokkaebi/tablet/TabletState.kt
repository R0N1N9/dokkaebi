package dev.dokkaebi.tablet

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dokkaebi.tablet.audio.Feedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class Status { IDLE, DIALING, RINGING, BREACHED }

data class Target(
    val id: Int,
    val name: String,
    val device: String,
    val status: Status = Status.IDLE,
    val since: Long = System.currentTimeMillis(),
)

data class LogLine(val time: String, val text: String, val level: Int = 0)

class TabletViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tablet", 0)
    private val feedback = Feedback(app)
    private val jobs = mutableMapOf<Int, Job>()
    private var nextId = 1

    val targets = mutableStateListOf<Target>()
    val log = mutableStateListOf<LogLine>()

    var charges by mutableIntStateOf(MAX_CHARGES); private set
    var recharge by mutableFloatStateOf(0f); private set
    var sound by mutableStateOf(prefs.getBoolean("sound", true)); private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true)); private set
    var autoHangUpSec by mutableIntStateOf(prefs.getInt("autoHangUp", 12)); private set

    init {
        val saved = prefs.getString("targets", null)?.split('\n')?.filter { it.isNotBlank() }
        (saved ?: DEFAULT_NAMES).forEach { addTarget(it, persist = false, announce = false) }
        log("LOGIC BOMB SUITE ONLINE", 1)
        log("${targets.size} DEVICES ON LOCAL MESH")
        viewModelScope.launch { rechargeLoop() }
    }

    private suspend fun rechargeLoop() {
        while (true) {
            delay(100)
            if (charges < MAX_CHARGES) {
                recharge += 100f / RECHARGE_MS
                if (recharge >= 1f) {
                    recharge = 0f
                    charges++
                    log("CHARGE RESTORED [$charges/$MAX_CHARGES]", 1)
                }
            } else recharge = 0f
        }
    }

    fun log(text: String, level: Int = 0) {
        log.add(LogLine(TIME.format(Date()), text, level))
        if (log.size > 200) log.removeAt(0)
    }

    private fun update(id: Int, f: (Target) -> Target) {
        val i = targets.indexOfFirst { it.id == id }
        if (i >= 0) targets[i] = f(targets[i])
        syncRing()
    }

    private fun syncRing() {
        feedback.setRinging(targets.any { it.status == Status.RINGING }, sound, haptics)
    }

    /** Place a call to one target: dial -> ring -> (hang up) -> breached. */
    fun call(id: Int, fromBomb: Boolean = false) {
        val t = targets.firstOrNull { it.id == id } ?: return
        if (t.status == Status.DIALING || t.status == Status.RINGING) return
        if (!fromBomb) feedback.blip(sound, haptics)
        jobs[id]?.cancel()
        jobs[id] = viewModelScope.launch {
            update(id) { it.copy(status = Status.DIALING, since = System.currentTimeMillis()) }
            log("DIAL ${t.name} :: ${t.device}")
            delay(Random.nextLong(700, 1600))
            update(id) { it.copy(status = Status.RINGING, since = System.currentTimeMillis()) }
            log("${t.name} RINGING", 2)
            if (autoHangUpSec > 0) {
                delay(autoHangUpSec * 1000L)
                breach(id)
            }
        }
    }

    /** The target hangs up — their device is now ours. */
    fun hangUp(id: Int) {
        jobs[id]?.cancel()
        feedback.blip(sound, haptics)
        breach(id)
    }

    private fun breach(id: Int) {
        val t = targets.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(status = Status.BREACHED, since = System.currentTimeMillis()) }
        log("${t.name} HUNG UP — DEVICE COMPROMISED", 1)
    }

    fun reset(id: Int) {
        jobs[id]?.cancel()
        update(id) { it.copy(status = Status.IDLE, since = System.currentTimeMillis()) }
    }

    /** Ring every idle device at once, staggered for effect. Consumes one charge. */
    fun logicBomb(): Boolean {
        if (charges <= 0) {
            log("NO CHARGES — RECHARGING", 2)
            feedback.blip(sound, haptics)
            return false
        }
        val victims = targets.filter { it.status == Status.IDLE }
        if (victims.isEmpty()) {
            log("NO IDLE DEVICES IN RANGE", 2)
            return false
        }
        charges--
        feedback.alarm(sound, haptics)
        log(">>> LOGIC BOMB DEPLOYED [$charges/$MAX_CHARGES] <<<", 2)
        viewModelScope.launch {
            victims.forEach { delay(Random.nextLong(60, 220)); call(it.id, fromBomb = true) }
        }
        return true
    }

    fun hangUpAll() {
        targets.filter { it.status == Status.RINGING || it.status == Status.DIALING }.forEach { hangUp(it.id) }
    }

    fun resetAll() {
        targets.forEach { jobs[it.id]?.cancel() }
        for (i in targets.indices) targets[i] = targets[i].copy(status = Status.IDLE)
        syncRing()
        log("ALL LINKS DROPPED")
    }

    fun addTarget(name: String, persist: Boolean = true, announce: Boolean = true) {
        val clean = name.trim().uppercase().take(18).ifEmpty { return }
        targets.add(Target(nextId++, clean, randomDevice()))
        if (announce) log("NEW DEVICE TAGGED: $clean")
        if (persist) save()
    }

    fun rename(id: Int, name: String) {
        val clean = name.trim().uppercase().take(18).ifEmpty { return }
        update(id) { it.copy(name = clean) }
        save()
    }

    fun remove(id: Int) {
        jobs.remove(id)?.cancel()
        targets.removeAll { it.id == id }
        syncRing()
        save()
    }

    fun updateSound(on: Boolean) { sound = on; prefs.edit().putBoolean("sound", on).apply(); feedback.setRinging(false, false, false); syncRing() }
    fun updateHaptics(on: Boolean) { haptics = on; prefs.edit().putBoolean("haptics", on).apply(); feedback.setRinging(false, false, false); syncRing() }
    fun updateAutoHangUp(sec: Int) { autoHangUpSec = sec; prefs.edit().putInt("autoHangUp", sec).apply() }

    fun pauseFeedback() = feedback.setRinging(false, false, false)
    fun resumeFeedback() = syncRing()

    private fun save() = prefs.edit().putString("targets", targets.joinToString("\n") { it.name }).apply()

    override fun onCleared() {
        jobs.values.forEach { it.cancel() }
        feedback.release()
    }

    companion object {
        const val MAX_CHARGES = 2
        const val RECHARGE_MS = 20_000f
        private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)
        private val DEFAULT_NAMES = listOf("ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO")
        private fun randomDevice(): String {
            val hex = "0123456789ABCDEF"
            return "MAC " + (1..6).joinToString(":") { "${hex.random()}${hex.random()}" }
        }
    }
}
