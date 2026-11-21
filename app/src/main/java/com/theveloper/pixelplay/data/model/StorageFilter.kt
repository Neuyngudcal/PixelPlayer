package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class StorageFilter(val value: Int) {
    ALL(0),
    OFFLINE(1),
    ONLINE(2)
}
