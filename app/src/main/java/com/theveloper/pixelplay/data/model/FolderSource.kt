package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class FolderSource(val storageKey: String, val displayName: String) {
    INTERNAL("internal", "Internal Storage"),
    SD_CARD("sd_card", "SD Card");

    companion object {
        fun fromStorageKey(rawValue: String?): FolderSource =
            entries.firstOrNull { it.storageKey == rawValue } ?: INTERNAL
    }
}
