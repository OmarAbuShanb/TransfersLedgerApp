package dev.anonymous.transfers_ledger.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.anonymous.transfers_ledger.R
import dev.anonymous.transfers_ledger.data.local.db.CustomerEntity
import dev.anonymous.transfers_ledger.databinding.BottomSheetLinkCustomerBinding
import dev.anonymous.transfers_ledger.ui.adapters.CustomerAdapter
import dev.anonymous.transfers_ledger.ui.common.AppDialogs
import kotlin.time.Duration.Companion.milliseconds

class CustomerLinkBottomSheetDialogFragment : BottomSheetDialogFragment() {

    var searchCustomers: (suspend (String) -> List<CustomerEntity>)? = null
    var onLinkListener: ((CustomerEntity) -> Unit)? = null

    private var _binding: BottomSheetLinkCustomerBinding? = null
    private val binding get() = _binding!!
    private var searchJob: Job? = null

    companion object {
        fun newInstance() = CustomerLinkBottomSheetDialogFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLinkCustomerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CustomerAdapter { customer ->
            binding.searchInput.clearFocus()
            hideKeyboard(requireContext(), binding.searchInput)

            AppDialogs.showConfirmation(
                fragmentManager = parentFragmentManager,
                title = getString(R.string.confirm_link_customer_title),
                message = getString(R.string.confirm_link_customer_message, customer.displayName),
                positiveText = getString(R.string.link)
            ) {
                dismissAllowingStateLoss()
                onLinkListener?.invoke(customer)
            }
        }

        binding.customersRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.customersRecycler.adapter = adapter

        fun runSearch() {
            val query = binding.searchInput.text.toString().trim()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(180.milliseconds)
                val search = searchCustomers
                if (search != null) {
                    val customers = search(query)
                    if (_binding != null) {
                        adapter.submitList(customers)
                        binding.emptyText.text = getString(
                            if (query.isBlank()) R.string.no_customers else R.string.no_matching_customers
                        )
                        binding.emptyText.visibility =
                            if (customers.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = runSearch()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        (dialog as? BottomSheetDialog)?.let { bsDialog ->
            AppDialogs.configureDialogWindowSystemBars(bsDialog.window, requireContext())
        }

        runSearch()
        focusAndShowKeyboard(requireContext(), binding.searchInput)
    }

    override fun onStart() {
        super.onStart()
        val bsDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = bsDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheet.setBackgroundResource(R.drawable.bg_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        val displayMetrics = resources.displayMetrics
        val minHeight = (displayMetrics.heightPixels * 0.55).toInt()
        bottomSheet.minimumHeight = minHeight
        behavior.peekHeight = minHeight
        bsDialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    private fun focusAndShowKeyboard(context: Context, view: View) {
        view.postDelayed({
            if (isAdded) {
                view.requestFocus()
                context.getSystemService<InputMethodManager>()
                    ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 160L)
    }

    private fun hideKeyboard(context: Context, view: View) {
        context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
