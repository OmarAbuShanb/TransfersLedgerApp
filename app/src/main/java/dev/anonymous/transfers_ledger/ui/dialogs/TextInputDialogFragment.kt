package dev.anonymous.transfers_ledger.ui.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import dev.anonymous.transfers_ledger.databinding.DialogTextInputBinding

class TextInputDialogFragment : BaseAnimatedDialogFragment() {

    override val motion = Motion.BOTTOM_SCALE_FADE
    override val softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

    // Tell the base class exactly which field should gain focus and show the keyboard.
    override fun getFocusTarget(): EditText? = _binding?.inputText

    var onSaveListener: ((String) -> Unit)? = null

    private var title: String = ""
    private var hint: String = ""
    private var initialValue: String = ""

    private var _binding: DialogTextInputBinding? = null

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_HINT = "hint"
        private const val ARG_INITIAL = "initial_value"
        private const val MAX_LENGTH = 40

        fun newInstance(title: String, hint: String, initialValue: String): TextInputDialogFragment {
            return TextInputDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_HINT, hint)
                    putString(ARG_INITIAL, initialValue)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            title = it.getString(ARG_TITLE, "")
            hint = it.getString(ARG_HINT, "")
            initialValue = it.getString(ARG_INITIAL, "")
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val binding = DialogTextInputBinding.inflate(inflater, container, false)
        _binding = binding

        binding.titleText.text = title
        binding.inputText.hint = hint
        binding.inputText.filters = arrayOf(InputFilter.LengthFilter(MAX_LENGTH))
        binding.inputText.setText(initialValue)
        binding.inputText.setSelection(binding.inputText.text?.length ?: 0)

        binding.inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.errorText.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        binding.cancelButton.setOnClickListener { dismissWithAnimation() }
        binding.saveButton.setOnClickListener {
            val value = binding.inputText.text.toString().trim()
            when {
                value.isBlank() -> {
                    binding.errorText.text = "يرجى إدخال اسم"
                    binding.errorText.visibility = View.VISIBLE
                }
                value.length < 2 -> {
                    binding.errorText.text = "يجب أن يتكون الاسم من حرفين على الأقل"
                    binding.errorText.visibility = View.VISIBLE
                }
                else -> {
                    onSaveListener?.invoke(value)
                    dismissWithAnimation()
                }
            }
        }
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
