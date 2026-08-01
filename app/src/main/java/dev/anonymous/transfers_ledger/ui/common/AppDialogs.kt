package dev.anonymous.transfers_ledger.ui.common

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.databinding.BottomSheetLinkCustomerBinding
import dev.anonymous.transfers_ledger.databinding.DialogExportNoticeBinding
import dev.anonymous.transfers_ledger.databinding.DialogTextInputBinding
import dev.anonymous.transfers_ledger.ui.adapters.CustomerAdapter

private enum class DialogMotion {
    BOTTOM_SCALE_FADE,
    HORIZONTAL_SLIDE
}

object AppDialogs {
    private var activeDialog: Dialog? = null
    private var activeBottomSheet: BottomSheetDialog? = null

    fun configureDialogWindowSystemBars(window: Window?, context: Context) {
        window ?: return
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val activityWindow = (context as? Activity)?.window
            ?: (context as? ContextWrapper)?.baseContext?.let { (it as? Activity)?.window }

        if (activityWindow != null) {
            val hostController = WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
            val dialogController = WindowCompat.getInsetsController(window, window.decorView)
            dialogController.isAppearanceLightStatusBars = hostController.isAppearanceLightStatusBars
            dialogController.isAppearanceLightNavigationBars = hostController.isAppearanceLightNavigationBars
        }
    }

    fun showTextInput(
        context: Context,
        title: String,
        hint: String,
        initialValue: String,
        onSave: (String) -> Unit
    ) {
        safeDismiss(activeDialog)

        val binding = DialogTextInputBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.BOTTOM_SCALE_FADE

        binding.titleText.text = title
        binding.inputText.hint = hint
        binding.inputText.setText(initialValue)
        binding.inputText.setSelection(binding.inputText.text?.length ?: 0)
        binding.cancelButton.setOnClickListener { dismissWithAnimation(dialog, binding.root, motion) }
        binding.saveButton.setOnClickListener {
            val value = binding.inputText.text.toString().trim()
            if (value.isNotBlank()) {
                onSave(value)
                dismissWithAnimation(dialog, binding.root, motion)
            }
        }

        showWide(
            dialog, binding.root, motion,
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        )
        focusAndShowKeyboard(context, binding.inputText)
    }

    fun showExportNotice(
        context: Context,
        onApply: () -> Unit
    ) {
        safeDismiss(activeDialog)

        val binding = DialogExportNoticeBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.HORIZONTAL_SLIDE

        binding.cancelButton.setOnClickListener { dismissWithAnimation(dialog, binding.root, motion) }
        binding.applyButton.setOnClickListener {
            dismissWithAnimation(dialog, binding.root, motion, onApply)
        }

        showWide(dialog, binding.root, motion)
    }

    fun showOverview(context: Context) {
        safeDismiss(activeDialog)

        val binding = dev.anonymous.transfers_ledger.databinding.DialogOverviewBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.HORIZONTAL_SLIDE

        binding.okButton.setOnClickListener {
            dismissWithAnimation(dialog, binding.root, motion)
        }

        showWide(dialog, binding.root, motion)
    }

    fun showConfirmation(
        context: Context,
        title: String,
        message: String,
        positiveText: String,
        onConfirm: () -> Unit
    ) {
        safeDismiss(activeDialog)

        val binding = DialogExportNoticeBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.BOTTOM_SCALE_FADE

        binding.titleText.text = title
        binding.messageText.text = message
        binding.applyButton.text = positiveText
        binding.cancelButton.setOnClickListener { dismissWithAnimation(dialog, binding.root, motion) }
        binding.applyButton.setOnClickListener {
            dismissWithAnimation(dialog, binding.root, motion, onConfirm)
        }

        showWide(dialog, binding.root, motion)
    }

    fun showActivationDialog(
        context: Context,
        deviceIdHash: String,
        message: String,
        isCancelable: Boolean,
        onWhatsappClick: () -> Unit,
        onActivate: (String) -> Boolean
    ) {
        safeDismiss(activeDialog)

        val binding = dev.anonymous.transfers_ledger.databinding.DialogActivationBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.BOTTOM_SCALE_FADE
        
        dialog.setCancelable(isCancelable)
        if (!isCancelable) {
            dialog.setOnKeyListener { _, keyCode, event ->
                keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP
            }
        }
        
        binding.messageText.text = message
        binding.deviceIdText.text = context.getString(dev.anonymous.transfers_ledger.R.string.license_device_id, deviceIdHash)
        
        binding.whatsappButton.setOnClickListener {
            onWhatsappClick()
        }

        binding.applyButton.setOnClickListener {
            val code = binding.inputText.text.toString().trim()
            if (code.isNotBlank()) {
                val success = onActivate(code)
                if (success) {
                    dismissWithAnimation(dialog, binding.root, motion)
                } else {
                    binding.errorText.visibility = android.view.View.VISIBLE
                    binding.errorText.text = context.getString(dev.anonymous.transfers_ledger.R.string.license_error_invalid_code)
                }
            } else {
                binding.errorText.visibility = android.view.View.VISIBLE
                binding.errorText.text = context.getString(dev.anonymous.transfers_ledger.R.string.license_error_empty_code)
            }
        }

        showWide(
            dialog, binding.root, motion,
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN,
            dismissOnOutsideClick = isCancelable
        )
    }

    fun showFeatureNotAvailableDialog(
        context: Context,
        onActivateClick: () -> Unit
    ) {
        safeDismiss(activeDialog)
        
        val binding = dev.anonymous.transfers_ledger.databinding.DialogFeatureLockedBinding.inflate(LayoutInflater.from(context))
        val dialog = createDialog(context)
        val motion = DialogMotion.BOTTOM_SCALE_FADE
        
        dialog.setCancelable(true)
        
        binding.cancelButton.setOnClickListener {
            dismissWithAnimation(dialog, binding.root, motion)
        }
        
        binding.activateButton.setOnClickListener {
            dismissWithAnimation(dialog, binding.root, motion) {
                onActivateClick()
            }
        }
        
        showWide(
            dialog, binding.root, motion,
            dismissOnOutsideClick = true
        )
    }

    fun showCustomerLinkSheet(
        context: Context,
        lifecycleScope: LifecycleCoroutineScope,
        searchCustomers: suspend (String) -> List<CustomerEntity>,
        onLink: (CustomerEntity) -> Unit
    ) {
        if (activeBottomSheet?.isShowing == true) return

        val binding = BottomSheetLinkCustomerBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        var searchJob: Job? = null

        val adapter = CustomerAdapter { customer ->
            binding.searchInput.clearFocus()
            hideKeyboard(context, binding.searchInput)

            showConfirmation(
                context = context,
                title = context.getString(R.string.confirm_link_customer_title),
                message = context.getString(R.string.confirm_link_customer_message, customer.displayName),
                positiveText = context.getString(R.string.link)
            ) {
                safeDismiss(dialog)
                onLink(customer)
            }
        }

        binding.customersRecycler.layoutManager = LinearLayoutManager(context)
        binding.customersRecycler.adapter = adapter

        fun runSearch() {
            val query = binding.searchInput.text.toString().trim()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(180)
                val customers = searchCustomers(query)
                adapter.submitList(customers)
                binding.emptyText.text = context.getString(
                    if (query.isBlank()) R.string.no_customers else R.string.no_matching_customers
                )
                binding.emptyText.visibility =
                    if (customers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = runSearch()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        dialog.setContentView(binding.root)
        dialog.setOnDismissListener {
            searchJob?.cancel()
            if (activeBottomSheet == dialog) activeBottomSheet = null
        }
        dialog.setOnShowListener {
            val bottomSheet =
                dialog.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        }
        activeBottomSheet = dialog
        dialog.show()
        configureDialogWindowSystemBars(dialog.window, context)
        runSearch()
        focusAndShowKeyboard(context, binding.searchInput)
    }

    private fun createDialog(context: Context): Dialog {
        return Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun showWide(
        dialog: Dialog,
        content: android.view.View,
        motion: DialogMotion,
        softInputMode: Int = 0,
        dismissOnOutsideClick: Boolean = true
    ) {
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog == dialog) activeDialog = null
        }

        configureDialogWindowSystemBars(dialog.window, dialog.context)

        val hMarginPx = dpToPx(dialog.context, 24)
        val wrapper = FrameLayout(dialog.context).apply {
            clipChildren = false
            clipToPadding = false
        }
        val contentParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        contentParams.marginStart = hMarginPx
        contentParams.marginEnd = hMarginPx
        wrapper.addView(content, contentParams)

        if (dismissOnOutsideClick) {
            wrapper.setOnClickListener {
                dismissWithAnimation(dialog, content, motion)
            }
        }
        content.setOnClickListener {
            // Consume clicks inside the content card so they don't propagate to wrapper
        }

        dialog.setContentView(wrapper)
        if (softInputMode != 0) {
            dialog.window?.setSoftInputMode(softInputMode)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        animateDialogContent(content, motion, entering = true)
    }

    private fun safeDismiss(dialog: Dialog?) {
        if (dialog == null) return
        try {
            if (dialog.isShowing) {
                val context = dialog.context
                if (context is android.app.Activity) {
                    if (!context.isFinishing && !context.isDestroyed) {
                        dialog.dismiss()
                    }
                } else {
                    dialog.dismiss()
                }
            }
        } catch (_: Exception) {
            // Ignore if window was already detached or activity destroyed
        }
    }

    private fun dismissWithAnimation(
        dialog: Dialog,
        content: android.view.View,
        motion: DialogMotion,
        afterDismiss: (() -> Unit)? = null
    ) {
        content.animate().cancel()
        animateDialogContent(content, motion, entering = false) {
            safeDismiss(dialog)
            afterDismiss?.invoke()
        }
    }

    private fun animateDialogContent(
        content: android.view.View,
        motion: DialogMotion,
        entering: Boolean,
        endAction: (() -> Unit)? = null
    ) {
        when (motion) {
            DialogMotion.BOTTOM_SCALE_FADE -> {
                if (entering) {
                    content.alpha = 0f
                    content.translationY = dpToPx(content.context, 42).toFloat()
                    content.scaleX = 0.96f
                    content.scaleY = 0.96f
                }
                content.animate()
                    .alpha(if (entering) 1f else 0f)
                    .translationY(if (entering) 0f else dpToPx(content.context, 42).toFloat())
                    .scaleX(if (entering) 1f else 0.96f)
                    .scaleY(if (entering) 1f else 0.96f)
                    .setDuration(220L)
                    .withEndAction { endAction?.invoke() }
                    .start()
            }
            DialogMotion.HORIZONTAL_SLIDE -> {
                if (entering) {
                    content.alpha = 0f
                    content.translationX = dpToPx(content.context, 64).toFloat()
                }
                content.animate()
                    .alpha(if (entering) 1f else 0f)
                    .translationX(if (entering) 0f else dpToPx(content.context, 64).toFloat())
                    .setDuration(220L)
                    .withEndAction { endAction?.invoke() }
                    .start()
            }
        }
    }

    private fun dpToPx(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun focusAndShowKeyboard(context: Context, view: android.view.View) {
        view.postDelayed({
            view.requestFocus()
            context.getSystemService<InputMethodManager>()
                ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 160L)
    }

    private fun hideKeyboard(context: Context, view: android.view.View) {
        context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
