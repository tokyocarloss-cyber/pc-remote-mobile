package com.pcremote.mobile

import androidx.compose.ui.unit.IntSize

val IntSize.minDimension: Int
    get() = minOf(width, height)
