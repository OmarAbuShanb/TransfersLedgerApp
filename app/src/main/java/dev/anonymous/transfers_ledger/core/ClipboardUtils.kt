package dev.anonymous.transfers_ledger.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import dev.anonymous.transfers_ledger.R

object ClipboardUtils {
    fun copy(context: Context, text: String, messageResId: Int = R.string.copied_to_clipboard) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, messageResId, Toast.LENGTH_SHORT).show()
    }
}
