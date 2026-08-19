package dev.anonymous.transfers_ledger.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.anonymous.transfers_ledger.databinding.DialogExportNoticeBinding

class ExportNoticeDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.HORIZONTAL_SLIDE

    var onApplyListener: (() -> Unit)? = null

    companion object {
        fun newInstance() = ExportNoticeDialogFragment()
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogExportNoticeBinding.inflate(inflater, container, false)
        binding.cancelButton.setOnClickListener { dismissWithAnimation() }
        binding.applyButton.setOnClickListener {
            dismissWithAnimation {
                onApplyListener?.invoke()
            }
        }
        return binding.root
    }
}
