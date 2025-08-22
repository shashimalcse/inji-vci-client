package com.example.vciclient.util

object NotificationCenter {
    private val listeners = mutableMapOf<String, MutableList<(String) -> Unit>>()

    fun post(event: String, value: String) {
        listeners[event]?.forEach { it(value) }
    }

    fun observe(event: String, handler: (String) -> Unit) {
        listeners.getOrPut(event) { mutableListOf() }.add(handler)
    }

    fun once(event: String, handler: (String) -> Unit) {
        var wrapper: ((String) -> Unit)? = null
        wrapper = { value ->
            handler(value)
            listeners[event]?.remove(wrapper)
        }
        observe(event, wrapper)
    }
}
