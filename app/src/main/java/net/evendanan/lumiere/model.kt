package net.evendanan.lumiere

import android.net.Uri

data class Media(val title: String, val preview: Uri, val original: Uri, val mediumQuality: Uri, val filename: String)