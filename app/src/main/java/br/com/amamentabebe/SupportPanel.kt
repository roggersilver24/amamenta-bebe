package br.com.amamentabebe

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Payment fields stay absent until the developer supplies a genuine beneficiary and payload. */
@Composable fun SupportPanel() {
    Text("Apoie o desenvolvedor", style = MaterialTheme.typography.titleMedium)
    Text("Contribuição opcional, sem desbloqueio de funcionalidades.")
    val payload = BuildConfig.PIX_COPY_PASTE.trim()
    val beneficiary = BuildConfig.PIX_BENEFICIARY.trim()
    // Validate the Pix EMV payload and CRC before presenting anything as payable.
    val configured = beneficiary.isNotBlank() && validPixPayload(payload)
    if (!configured) {
        Text("Dados Pix ainda não configurados.")
        return
    }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val qr = remember(payload) { runCatching {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512)
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }.asImageBitmap()
    }.getOrNull() }
    Text("Beneficiário: $beneficiary")
    qr?.let { Image(it, contentDescription = "QR Code Pix para $beneficiary", modifier = Modifier.size(240.dp)) }
    SelectionContainer { Text(payload, style = MaterialTheme.typography.bodySmall) }
    TextButton(onClick = { clipboard.setText(AnnotatedString(payload)); copied = true }) { Text(if (copied) "Código copiado" else "Copiar Pix Copia e Cola") }
}

internal fun validPixPayload(payload: String): Boolean {
    if (!payload.startsWith("000201") || payload.length !in 30..2048) return false
    if (payload.takeLast(8).take(4) != "6304" || !payload.contains("BR.GOV.BCB.PIX", ignoreCase = true)) return false
    val expected = payload.takeLast(4).toIntOrNull(16) ?: return false
    var crc = 0xFFFF
    payload.dropLast(4).toByteArray(Charsets.UTF_8).forEach { byte ->
        crc = crc xor ((byte.toInt() and 0xFF) shl 8)
        repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1; crc = crc and 0xFFFF }
    }
    return crc == expected
}
