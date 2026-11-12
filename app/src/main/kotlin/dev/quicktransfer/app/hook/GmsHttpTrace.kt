// SPDX-License-Identifier: Apache-2.0
package dev.quicktransfer.app.hook

import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.util.UUID

/** Read-only observers of the pinned GMS 26.43.30 HTTP boundary; no request/response replacement. */
internal class GmsHttpTrace(private val gms: GmsReflection, private val journal: HostJournal,
    private val task: () -> String?) {
    private val request = ThreadLocal<String>()
    fun install(module: XposedModule) {
        // wxa logs getHeaderFields(), its serialized body and redirect responses through wxm.a.
        module.hook(gms.declared("wxm", "a", String::class.java)).intercept { chain ->
            task()?.let { id -> journal.detail(id, "gms-native-log", JSONObject()
                .put("traceId", request.get()).put("text", chain.args[0])) }
            chain.proceed()
        }
        module.hook(gms.declared("wxa", "a", gms.type("wxd"), Int::class.javaPrimitiveType!!)).intercept { chain ->
            val id = task() ?: return@intercept chain.proceed()
            val parent = request.get()
            val trace = UUID.randomUUID().toString()
            request.set(trace)
            val start = System.nanoTime()
            val input = requireNotNull(chain.args[0])
            val metadata = JSONObject().put("traceId", trace).put("parentTraceId", parent)
                .put("redirectDepth", chain.args[1]).put("startedAt", System.currentTimeMillis())
                .put("url", gms.text(input, "a")).put("method", gms.text(input, "b"))
                .put("requestBody", gms.field(input, "c").toString().replace("\\/", "/"))
                .put("requestHeaders", JSONObject(gms.field(input, "d") as Map<*, *>))
                .put("timeoutSeconds", gms.number(input, "e"))
                .put("network", gms.field(input, "f")?.toString())
            try {
                journal.detail(id, "gms-http-start", metadata)
                chain.proceed().also { response ->
                    val result = requireNotNull(response)
                    metadata.put("response", gms.text(result, "b")).put("httpStatus", gms.number(result, "c"))
                        .put("responseMessage", gms.text(result, "d")).put("cookies", org.json.JSONArray(gms.field(result, "e") as List<*>))
                        .put("location", gms.text(result, "f"))
                }
            } catch (error: Throwable) {
                metadata.put("exceptionClass", error.javaClass.name).put("exceptionMessage", error.message)
                    .put("stackTrace", error.stackTraceToString())
                throw error
            } finally {
                request.set(parent)
                journal.detail(id, "gms-http-complete", metadata.put("endedAt", System.currentTimeMillis())
                    .put("durationMs", (System.nanoTime() - start) / 1_000_000))
            }
        }
    }
}
