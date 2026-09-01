package dev.ajithgoveas.kindex.core.io

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlin.native.OsFamily

@OptIn(ExperimentalNativeApi::class)
actual val isFileSystemCaseSensitive: Boolean = Platform.osFamily == OsFamily.LINUX
