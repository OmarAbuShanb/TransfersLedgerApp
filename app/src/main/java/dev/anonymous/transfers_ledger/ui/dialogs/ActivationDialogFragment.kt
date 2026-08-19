package dev.anonymous.transfers_ledger.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.databinding.DialogActivationBinding

class ActivationDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.BOTTOM_SCALE_FADE
    override val softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

    // Tell the base class exactly which field should gain focus and show the keyboard.
    override fun getFocusTarget(): EditText? = _binding?.inputText

    var onWhatsappClickListener: (() -> Unit)? = null
    var onActivateListener: ((String) -> Boolean)? = null

    private var deviceIdHash: String = ""
    private var message: String = ""
    private var isCancelableDialog: Boolean = true

    override val dismissOnOutsideClick: Boolean
        get() = isCancelableDialog

    private var _binding: DialogActivationBinding? = null

    companion object {
        private const val ARG_DEVICE_HASH = "device_hash"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CANCELABLE = "cancelable"

        fun newInstance(
            deviceIdHash: String,
            message: String,
            isCancelable: Boolean
        ): ActivationDialogFragment {
            return ActivationDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DEVICE_HASH, deviceIdHash)
                    putString(ARG_MESSAGE, message)
                    putBoolean(ARG_CANCELABLE, isCancelable)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            deviceIdHash = it.getString(ARG_DEVICE_HASH, "")
            message = it.getString(ARG_MESSAGE, "")
            isCancelableDialog = it.getBoolean(ARG_CANCELABLE, true)
        }
        isCancelable = isCancelableDialog
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogActivationBinding.inflate(inflater, container, false)
        _binding = binding

        binding.messageText.text = message
        binding.deviceIdText.text = getString(R.string.license_device_id, deviceIdHash)

        binding.whatsappButton.setOnClickListener {
            onWhatsappClickListener?.invoke()
        }

        binding.applyButton.setOnClickListener {
            val code = binding.inputText.text.toString().trim()
            if (code.isNotBlank()) {
                val success = onActivateListener?.invoke(code) ?: false
                if (success) {
                    dismissWithAnimation()
                } else {
                    binding.errorText.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.license_error_invalid_code)
                }
            } else {
                binding.errorText.visibility = View.VISIBLE
                binding.errorText.text = getString(R.string.license_error_empty_code)
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
