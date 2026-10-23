package com.dimkabuki.lanmonitor

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

internal fun createQrCodeBitmap(content: String, size: Int = 512): Bitmap {
    require(content.isNotBlank()) { "QR content must not be blank." }
    require(size >= 128) { "QR image size must be at least 128 pixels." }

    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 2),
    )
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
