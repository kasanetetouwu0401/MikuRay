package com.v2ray.ang.util

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.v2ray.ang.R
import com.v2ray.ang.databinding.DialogUrlTestProgressBinding
import com.v2ray.ang.databinding.ItemUrlTestResultBinding
import com.v2ray.ang.dto.TestProgressInfo
import com.v2ray.ang.handler.MmkvManager

class CountryCodeProgressDialogController(
    private val context: Context,
    private val onCancel: () -> Unit
) {
    private var dialog: AlertDialog? = null
    private var binding: DialogUrlTestProgressBinding? = null
    private val adapter = ResultAdapter()

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show(total: Int) {
        if (isShowing) {
            val b = binding ?: return
            adapter.clear()
            b.tvTitle.setText(R.string.title_country_code_all_server)
            b.progressIndicator.visibility = android.view.View.VISIBLE
            b.progressIndicator.isIndeterminate = true
            b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
            b.negativeButton.visibility = android.view.View.VISIBLE
            b.positiveButton.text = context.getString(R.string.action_minimize)
            b.positiveButton.setOnClickListener { dismiss() }
            return
        }

        val b = DialogUrlTestProgressBinding.inflate(LayoutInflater.from(context))
        binding = b
        b.tvTitle.setText(R.string.title_country_code_all_server)
        b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
        b.progressIndicator.isIndeterminate = true
        b.listView.layoutManager = LinearLayoutManager(context)
        b.listView.adapter = adapter
        adapter.clear()

        val d = MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setCancelable(false)
            .create()
        WindowBlurUtils.applyWindowBlur(d.window)

        b.negativeButton.setOnClickListener {
            dismiss()
            onCancel()
        }
        b.positiveButton.setOnClickListener { dismiss() }
        d.setOnDismissListener {
            dialog = null
            binding = null
        }
        d.show()
        dialog = d
    }

    fun update(info: TestProgressInfo) {
        val b = binding ?: return
        if (info.guid.isNotEmpty()) {
            val profile = MmkvManager.decodeServerConfig(info.guid)
            val code = MmkvManager.decodeServerAffiliationInfo(info.guid)?.countryCode
                ?.trim()?.uppercase()?.takeIf { it.length == 2 }
            adapter.append(
                ResultRow(
                    remarks = profile?.remarks.orEmpty(),
                    protocol = profile?.configType?.name.orEmpty(),
                    country = listOf(Utils.countryCodeToFlag(code), code)
                        .filterNotNull()
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                )
            )
            b.listView.post {
                if (adapter.itemCount > 0) b.listView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }
        if (b.progressIndicator.isIndeterminate) b.progressIndicator.isIndeterminate = false
        if (info.total > 0) {
            b.progressIndicator.setProgressCompat(
                ((info.current.toFloat() / info.total.toFloat()) * 100).toInt(), true
            )
        }
        b.tvCounter.text = context.getString(R.string.test_progress_counter, info.current, info.total)
    }

    fun finish() {
        val b = binding ?: return
        b.progressIndicator.isIndeterminate = false
        b.progressIndicator.setProgressCompat(100, true)
        b.progressIndicator.visibility = android.view.View.GONE
        b.negativeButton.visibility = android.view.View.GONE
        b.positiveButton.text = context.getString(android.R.string.ok)
        b.positiveButton.setOnClickListener { dismiss() }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
        adapter.clear()
    }

    private data class ResultRow(val remarks: String, val protocol: String, val country: String)

    private inner class ResultAdapter : RecyclerView.Adapter<ResultAdapter.RowHolder>() {
        private val rows = mutableListOf<ResultRow>()

        fun append(row: ResultRow) {
            rows.add(row)
            notifyItemInserted(rows.size - 1)
        }

        fun clear() {
            val size = rows.size
            if (size > 0) {
                rows.clear()
                notifyItemRangeRemoved(0, size)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            return RowHolder(ItemUrlTestResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(rows[position])
        override fun getItemCount() = rows.size

        inner class RowHolder(private val b: ItemUrlTestResultBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(row: ResultRow) {
                b.tvServerName.text = row.remarks
                b.tvProtocol.text = row.protocol
                b.tvPingResult.text = row.country.ifBlank { context.getString(R.string.country_code_unknown) }
            }
        }
    }
}

