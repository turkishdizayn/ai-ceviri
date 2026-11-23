package com.aiceviri.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(micGranted: Boolean, onRequestPermission: () -> Unit) {
    val vm: TranslatorViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val headset by vm.headset.collectAsStateWithLifecycle()

    BackHandler(enabled = state.screen != Screen.HOME) { vm.back() }

    when (state.screen) {
        Screen.HOME -> HomeScreen(
            settings = settings,
            headset = headset,
            micGranted = micGranted,
            onRequestPermission = onRequestPermission,
            onOpen = { mode -> if (micGranted) vm.open(mode) else onRequestPermission() },
            onPhoto = vm::openPhoto,
            onRecords = vm::openRecords,
            onSettings = vm::openSettings,
            onLanguageA = { lang -> vm.updateSettings { it.copy(languageA = lang) } },
            onLanguageB = { lang -> vm.updateSettings { it.copy(languageB = lang) } },
            onSwap = vm::swapLanguages,
        )
        Screen.CONVERSATION -> ConversationScreen(
            state = state,
            settings = settings,
            headset = headset,
            vm = vm,
        )
        Screen.PHOTO -> PhotoScreen(state = state, settings = settings, vm = vm)
        Screen.RECORDS -> RecordsScreen(state = state, vm = vm)
        Screen.RECORD_DETAIL -> {
            val record = state.selectedRecord
            if (record != null) RecordDetailScreen(record, vm) else RecordsScreen(state, vm)
        }
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = vm::updateSettings,
            onBack = vm::back,
        )
    }
}
