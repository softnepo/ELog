package com.lnsantos.elog

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Exception

internal const val ELogPackTAG = "ELogPack"

@OptIn(InternalCoroutinesApi::class)
open class ELogPack : ELogContract.Logger {

    private val interceptions = mutableListOf<ELog.Interception>()

    internal fun setup(
        middlewares: List<ELog.Interception>
    ) {
        synchronized(this) { interceptions.addAll(middlewares) }
    }

    @Synchronized
    private fun applyLog(
        level: ELog.Level,
        tag: String?,
        message: String?,
        exception: Throwable?,
    ) = runBlocking {

        var finalTag: String = tag ?: ELogPackTAG
        var messageFinal = message ?: exception?.message.toString()

        createTagByException(
            ignore = tag != null,
            exception = exception,
            onResult = { finalTag = it }
        )

        val scope = CoroutineScope(Dispatchers.IO)
        val break_ = "--------------------------------------"

        runCatching {
            scope.launch {
                interceptions.mapIndexed { index, interception ->
                    launch {
                        val progress = interception.onInterception(level, messageFinal, exception)

                        if (ELog.getShowInterceptionProgress()) {
                            Log.println(
                                ELog.Level.DEBUG.getPriority(),
                                finalTag,
                                "[$index] Class ${interception.javaClass.simpleName}::Progress ${progress.name}"
                            )
                            Log.println(level.getPriority(), finalTag, break_)
                        }

                        if (progress == ELog.Progress.CONTINUE) {
                            Log.println(level.getPriority(), finalTag, messageFinal)
                        }
                    }.join()
                }
                Log.println(level.getPriority(), finalTag, break_)
            }.join()
        }.onSuccess {
            Log.println(level.getPriority(), finalTag, break_)
        }.onFailure {
            Log.println(
                ELog.Level.ERROR.getPriority(),
                this::class.simpleName,
                it.stackTraceToString()
            )
            Log.println(level.getPriority(), finalTag, break_)
        }
    }

    @Synchronized
    override fun tag(tag: String): ELogContract.Logger = apply {
        throw NotImplementedError("tag isolate not implemented")
    }

    override fun d(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.DEBUG, null, message, null)
    }

    override fun d(exception: Throwable?): ELogContract.Logger = apply {
        applyLog(ELog.Level.DEBUG, null, null, exception)
    }

    override fun d(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.DEBUG, clazz.captureTag(), message, null)
    }

    override fun d(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.DEBUG, clazz.captureTag(), null, exception)
    }

    override fun v(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.VERBOSE, null, message, null)
    }

    override fun e(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ERROR, null, message, null)
    }

    override fun i(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.INFO, null, message, null)
    }

    override fun w(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.WARN, null, message, null)
    }

    override fun a(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ASSERT, null, message, null)
    }

    private inline fun createTagByException(
        ignore: Boolean = false,
        exception: Throwable?,
        onResult: (tagFiend: String) -> Unit
    ) {

        if (ignore) return

        (exception ?: Exception()).stackTrace
            ?.first()
            ?.className
            ?.substringAfterLast(".")
            ?.also { onResult(it) }
    }

    private fun createTagByException(
        ignore: Boolean = false,
        exception: Throwable?
    ) : String? {

        if (ignore) return null

        return (exception ?: Exception()).stackTrace
            ?.first()
            ?.className
            ?.substringAfterLast(".")
    }

    private fun Any?.captureTag() : String? {
        return this?.run { this::class.java.simpleName }
    }
}