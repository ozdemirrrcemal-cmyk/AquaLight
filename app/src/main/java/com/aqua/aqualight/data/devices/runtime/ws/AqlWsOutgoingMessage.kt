package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

sealed interface AqlWsOutgoingMessage {
    fun toJsonString(): String

    data class Auth(
        val token: String,
        val id: String = nextId(prefix = "auth")
    ) : AqlWsOutgoingMessage {
        override fun toJsonString(): String {
            return JSONObject()
                .put("id", id)
                .put("type", AqlWsContract.TYPE_AUTH)
                .put("token", token)
                .toString()
        }
    }

    data class Ping(
        val id: String = nextId()
    ) : AqlWsOutgoingMessage {
        override fun toJsonString(): String {
            return JSONObject()
                .put("id", id)
                .put("type", AqlWsContract.TYPE_PING)
                .toString()
        }
    }

    data class Command(
        val module: String,
        val action: String,
        val data: JSONObject = JSONObject(),
        val id: String = nextId()
    ) : AqlWsOutgoingMessage {
        override fun toJsonString(): String {
            return JSONObject()
                .put("id", id)
                .put("type", AqlWsContract.TYPE_COMMAND)
                .put("module", module)
                .put("action", action)
                .put("data", data)
                .toString()
        }
    }

    companion object {
        private val commandCounter = AtomicLong(0L)

        fun nextId(prefix: String = "android"): String {
            return "$prefix-${System.currentTimeMillis()}-${commandCounter.incrementAndGet()}"
        }
    }
}
