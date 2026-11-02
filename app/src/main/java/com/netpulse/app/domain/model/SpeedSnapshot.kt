package com.netpulse.app.domain.model

data class SpeedSnapshot(
    val downloadBytesPerSec: Long = 0L,
    val uploadBytesPerSec: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalBytesPerSec: Long get() = downloadBytesPerSec + uploadBytesPerSec
    val isIdle: Boolean get() = downloadBytesPerSec == 0L && uploadBytesPerSec == 0L

    /**
     * Rate the status-bar glyph should render.
     *
     * The glyph used [totalBytesPerSec] unconditionally, so hiding the upload
     * half removed it from the notification title and left it in the icon.
     * On an upload-heavy transfer the two surfaces then disagreed: the title
     * read `down 0 B/s` while the icon showed the upload rate.
     */
    fun displayBytesPerSec(includeUpload: Boolean): Long =
        if (includeUpload) totalBytesPerSec else downloadBytesPerSec
}
