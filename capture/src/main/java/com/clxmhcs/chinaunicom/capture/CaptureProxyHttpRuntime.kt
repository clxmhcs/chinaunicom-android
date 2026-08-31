package com.clxmhcs.chinaunicom.capture

/**
 * Bounded structured-message runtime for the source-parity local proxy.
 *
 * It owns no body bytes and receives only CaptureHttpMessage values that already passed through
 * CaptureHttpHeaderParser, including sensitive-header redaction.
 */
internal object CaptureProxyHttpRuntime {
    private const val RECENT_MESSAGE_LIMIT = 128
    private val lock = Any()
    private val recentMessages = ArrayDeque<CaptureHttpMessage>(RECENT_MESSAGE_LIMIT)
    private var snapshot = CaptureHttpSessionSnapshot()

    fun beginSession() {
        synchronized(lock) {
            recentMessages.clear()
            snapshot = CaptureHttpSessionSnapshot()
        }
    }

    fun publish(message: CaptureHttpMessage) {
        synchronized(lock) {
            if (recentMessages.size >= RECENT_MESSAGE_LIMIT) recentMessages.removeFirst()
            recentMessages.addLast(message)
            snapshot = snapshot.copy(
                messageCount = snapshot.messageCount + 1,
                requestCount = snapshot.requestCount + if (message.kind == CaptureHttpMessageKind.REQUEST) 1 else 0,
                responseCount = snapshot.responseCount + if (message.kind == CaptureHttpMessageKind.RESPONSE) 1 else 0,
            )
        }
    }

    fun snapshot(): CaptureHttpSessionSnapshot = synchronized(lock) { snapshot }

    fun recentMessages(): List<CaptureHttpMessage> = synchronized(lock) { recentMessages.toList() }
}
