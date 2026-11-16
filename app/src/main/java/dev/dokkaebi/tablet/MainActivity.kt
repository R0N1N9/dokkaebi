package dev.dokkaebi.tablet

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.dokkaebi.tablet.ui.BootScreen
import dev.dokkaebi.tablet.ui.DokkaebiTheme
import dev.dokkaebi.tablet.ui.TabletScreen
import dev.dokkaebi.tablet.ui.crt

class MainActivity : ComponentActivity() {
    private val vm: TabletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            DokkaebiTheme {
                var booted by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().crt()) {
                    Crossfade(booted, animationSpec = tween(400), label = "boot") { done ->
                        if (done) TabletScreen(vm) else BootScreen { booted = true }
                    }
                }
            }
        }
    }

    override fun onStop() { super.onStop(); vm.pauseFeedback() }
    override fun onStart() { super.onStart(); vm.resumeFeedback() }
}
