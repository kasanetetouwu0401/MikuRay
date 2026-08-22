package com.miku.ray.ui.server.fields

import android.app.Activity
import android.view.View
import android.view.ViewStub
import com.miku.ray.R

/**
 * Defers inflation of the large transport/TLS form sections until after the first frame.
 * Call [ensure] from any action that needs the fields immediately (for example Save).
 */
class ServerAdvancedFieldsLoader(
    private val activity: Activity,
    private val includeTransport: Boolean,
    private val includeTls: Boolean,
    private val onReady: (TransportFields?, TlsFields?) -> Unit,
) {
    private var ready = false

    fun schedule() {
        activity.window.decorView.postOnAnimation { ensure() }
    }

    fun ensure() {
        if (ready) return

        if (includeTransport) {
            activity.findViewById<ViewStub>(R.id.stub_transport)?.inflate()
        }
        if (includeTls) {
            activity.findViewById<ViewStub>(R.id.stub_tls)?.inflate()
        }

        val transportFields = if (includeTransport) TransportFields(activity) else null
        val tlsFields = if (includeTls) TlsFields(activity) else null
        ready = true
        onReady(transportFields, tlsFields)
    }

    val isReady: Boolean
        get() = ready
}
