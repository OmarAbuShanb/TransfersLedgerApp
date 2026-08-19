package dev.anonymous.transfers_ledger.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.anonymous.transfers_ledger.databinding.DialogExportNoticeBinding

class ConfirmationDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.BOTTOM_SCALE_FADE

    var onConfirmListener: (() -> Unit)? = null

    private var title: String = ""
    private var message: String = ""
    private var positiveText: String = ""

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_POSITIVE_TEXT = "positive_text"

        fun newInstance(title: String, message: String, positiveText: String): ConfirmationDialogFragment {
            return ConfirmationDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MESSAGE, message)
                    putString(ARG_POSITIVE_TEXT, positiveText)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            title = it.getString(ARG_TITLE, "")
            message = it.getString(ARG_MESSAGE, "")
            positiveText = it.getString(ARG_POSITIVE_TEXT, "")
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogExportNoticeBinding.inflate(inflater, container, false)
        binding.titleText.text = title
        binding.messageText.text = message
        binding.applyButton.text = positiveText

        binding.cancelButton.setOnClickListener { dismissWithAnimation() }
        binding.applyButton.setOnClickListener {
            dismissWithAnimation {
                onConfirmListener?.invoke()
            }
        }
        return binding.root
    }
}
