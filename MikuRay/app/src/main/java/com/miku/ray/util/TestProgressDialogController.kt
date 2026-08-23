package com.miku.ray.util

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.ArrayDeque
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.miku.ray.R
import com.miku.ray.databinding.DialogUrlTestProgressBinding
import com.miku.ray.databinding.ItemUrlTestResultBinding
import com.miku.ray.dto.TestProgressInfo
import com.miku.ray.extension.vibrateOnError
import com.miku.ray.handler.MmkvManager

/**
 * Progress dialog controller shared by the URL/TCP ping test and the country
 * code lookup test. Both tests use the same dialog + result list layout and
 * only differ in how a single result row is rendered, so [Mode] picks the
 * per-row formatting instead of keeping two near-identical classes/layouts.
 */
class TestProgressDialogController(
    private val context: Context,
    private val mode: Mode,
    private val onCancel: () -> Unit
) {
    enum class Mode { URL_TEST, COUNTRY_CODE }

    private var dialog: AlertDialog? = null
    private var binding: DialogUrlTestProgressBinding? = null
    private val adapter = ResultAdapter()
    private val pendingRows = ArrayDeque<ResultRow>()
    private var rowDrainScheduled = false
    private var finishRequested = false

    private val rowDrainRunnable = object : Runnable {
        override fun run() {
            rowDrainScheduled = false
            val b = binding ?: return
            val row = if (pendingRows.isEmpty()) null else pendingRows.removeFirst()
            if (row != null) {
                adapter.append(row)
                if (adapter.itemCount > 0) {
                    b.listView.smoothScrollToPosition(adapter.itemCount - 1)
                }
            }

            if (pendingRows.isNotEmpty()) {
                rowDrainScheduled = true
                b.listView.postDelayed(this, ROW_REVEAL_INTERVAL_MS)
            } else if (finishRequested) {
                applyFinishedState(b)
            }
        }
    }

    val isShowing: Boolean
        get() = dialog?.isShowing == true

    fun show(total: Int, @StringRes titleResId: Int = defaultTitleResId()) {
        if (isShowing) {
            val b = binding ?: return
            b.listView.removeCallbacks(rowDrainRunnable)
            pendingRows.clear()
            rowDrainScheduled = false
            finishRequested = false
            adapter.clear()
            b.tvTitle.setText(titleResId)
            b.progressIndicator.visibility = View.VISIBLE
            b.progressIndicator.isIndeterminate = true
            b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
            dialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility = View.VISIBLE
            dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setText(R.string.action_minimize)
            return
        }

        val b = DialogUrlTestProgressBinding.inflate(LayoutInflater.from(context))
        binding = b
        b.tvTitle.setText(titleResId)
        b.tvCounter.text = context.getString(R.string.test_progress_counter, 0, total)
        b.progressIndicator.isIndeterminate = true
        b.listView.layoutManager = LinearLayoutManager(context)
        b.listView.adapter = adapter
        pendingRows.clear()
        rowDrainScheduled = false
        finishRequested = false
        adapter.clear()

        val d = MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onCancel()
            }
            .setPositiveButton(R.string.action_minimize, null)
            .create()

        WindowBlurUtils.applyWindowBlur(d.window)

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
            val content = rowContentFor(info)
            pendingRows.addLast(
                ResultRow(
                    remarks = profile?.remarks.orEmpty(),
                    protocol = profile?.configType?.name.orEmpty(),
                    resultText = content.text,
                    resultColorRes = content.colorRes
                )
            )
            scheduleRowDrain(b)
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
        finishRequested = true
        if (pendingRows.isEmpty()) applyFinishedState(b)
        else scheduleRowDrain(b)
    }

    fun dismiss() {
        binding?.listView?.removeCallbacks(rowDrainRunnable)
        pendingRows.clear()
        rowDrainScheduled = false
        finishRequested = false
        dialog?.dismiss()
        dialog = null
        binding = null
        adapter.clear()
    }

    private fun scheduleRowDrain(b: DialogUrlTestProgressBinding) {
        if (rowDrainScheduled || binding !== b) return
        rowDrainScheduled = true
        b.listView.post(rowDrainRunnable)
    }

    private fun applyFinishedState(b: DialogUrlTestProgressBinding) {
        finishRequested = false
        b.progressIndicator.visibility = View.GONE
        dialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.visibility = View.GONE
        dialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.setText(android.R.string.ok)
    }

    private fun defaultTitleResId() = when (mode) {
        Mode.URL_TEST -> R.string.title_real_ping_all_server
        Mode.COUNTRY_CODE -> R.string.title_country_code_all_server
    }

    /** Text + color for a single result row, resolved once per [mode]. */
    private data class RowContent(val text: String, @androidx.annotation.ColorRes val colorRes: Int)

    private fun rowContentFor(info: TestProgressInfo): RowContent = when (mode) {
        Mode.URL_TEST -> {
            if (info.delayMillis > 0L) {
                RowContent(context.getString(R.string.test_progress_ping_ms, info.delayMillis), R.color.colorPing)
            } else {
                context.vibrateOnError()
                RowContent(context.getString(R.string.connection_test_fail), R.color.colorPingRed)
            }
        }
        Mode.COUNTRY_CODE -> {
            val code = MmkvManager.decodeServerAffiliationInfo(info.guid)?.countryCode
                ?.trim()?.uppercase()?.takeIf { it.length == 2 }
            if (code == null) {
                context.vibrateOnError()
                RowContent(context.getString(R.string.toast_failure), R.color.colorPingRed)
            } else {
                val text = listOf(Utils.countryCodeToFlag(code), code)
                    .filterNotNull()
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { context.getString(R.string.toast_failure) }
                RowContent(text, R.color.colorPing)
            }
        }
    }

    private data class ResultRow(
        val remarks: String,
        val protocol: String,
        val resultText: String,
        @androidx.annotation.ColorRes val resultColorRes: Int
    )

    private companion object {
        const val ROW_REVEAL_INTERVAL_MS = 70L
    }

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
                b.tvPingResult.text = row.resultText
                b.tvPingResult.setTextColor(ContextCompat.getColor(context, row.resultColorRes))
            }
        }
    }
}
