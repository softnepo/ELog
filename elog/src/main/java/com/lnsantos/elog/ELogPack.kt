package com.lnsantos.elog

import android.util.Log
import com.lnsantos.elog.annotation.ELogExperimental
import com.lnsantos.elog.core.ELogContract
import com.lnsantos.elog.print.LogSimplePrint
import com.lnsantos.elog.print.ProgressAnalyticsPrint
import com.lnsantos.elog.util.captureTag
import com.lnsantos.elog.util.createTagByException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

@OptIn(InternalCoroutinesApi::class)
@ELogExperimental
open class ELogPack(
    private val dispatcher: CoroutineContext = Dispatchers.IO
) : ELogContract.Logger {

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

        val mutexProgressAnalytics = Mutex()
        val mutexLogSimple = Mutex()

        var finalTag: String = tag ?: this@ELogPack::class.simpleName ?: "ELogPack"
        val messageFinal = message ?: exception?.message.toString()
        val printProgressAnalytics = ProgressAnalyticsPrint()
        val printLogSimple = LogSimplePrint()

        if (ELog.getShowInterceptionProgress()) {
            createTagByException(
                ignore = tag != null,
                exception = exception,
                onResult = {
                    finalTag = it
                }
            )
        }

        runCatching {
            interceptions.mapIndexed { _, interception ->
                launch(dispatcher) {

                    val progress = interception.onInterception(level, messageFinal, exception)

                    mutexProgressAnalytics.runBlockWithLog<ProgressAnalyticsPrint>(
                        level,
                        tag
                    ) {
                        printProgressAnalytics.onPrint(
                            tag = finalTag,
                            level = level,
                            interception = interception,
                            progress = progress,
                            message = null
                        )
                    }

                    if (progress == ELog.Progress.CONTINUE) {
                        mutexLogSimple.runBlockWithLog<LogSimplePrint>(
                            level,
                            tag
                        ) {
                            printLogSimple.onPrint(
                                tag = finalTag,
                                level = level,
                                interception = interception,
                                progress = progress,
                                message = messageFinal
                            )
                        }
                    }
                }.join()
            }
        }.onSuccess {
            Log.println(level.getPriority(), finalTag, String())
        }.onFailure {
            Log.println(
                ELog.Level.ERROR.getPriority(),
                this::class.simpleName,
                it.stackTraceToString()
            )
        }
    }

    private suspend inline fun <reified T> Mutex.runBlockWithLog(
        level: ELog.Level,
        tag: String? = null,
        action: () -> Unit
    ) {
        try {
            registerLogAnalytic<T>(
                level,
                tag
            )
            withLock(owner = T::class.simpleName) { action() }
        } catch (e: Throwable) {
            Log.println(
                level.getPriority(),
                tag,
                e.printStackTrace().toString()
            )
        } finally {
            registerLogAnalytic<T>(
                level,
                tag
            )
        }
    }

    private inline fun <reified T> Mutex.registerLogAnalytic(
        level: ELog.Level,
        tag: String? = null
    ) {
        if (ELog.getShowInterceptionProgress()) {
            Log.println(
                level.getPriority(),
                tag,
                "state ${T::class.simpleName} locked is $isLocked"
            )
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

    override fun v(exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.VERBOSE, null, null, exception)
    }

    override fun v(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.VERBOSE, clazz.captureTag(), message, null)
    }

    override fun v(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.VERBOSE, clazz.captureTag(), null, exception)
    }

    override fun e(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ERROR, null, message, null)
    }

    override fun e(exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.ERROR, null, null, exception)
    }

    override fun e(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ERROR, clazz.captureTag(), message, null)
    }

    override fun e(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.ERROR, clazz.captureTag(), null, exception)
    }

    override fun i(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.INFO, null, message, null)
    }

    override fun i(exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.INFO, null, null, exception)
    }

    override fun i(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.INFO, clazz.captureTag(), message, null)
    }

    override fun i(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.INFO, clazz.captureTag(), null, exception)
    }

    override fun w(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.WARN, null, message, null)
    }

    override fun w(exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.WARN, null, null, exception)
    }

    override fun w(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.WARN, clazz.captureTag(), message, null)
    }

    override fun w(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.WARN, clazz.captureTag(), null, exception)
    }

    override fun a(message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ASSERT, null, message, null)
    }

    override fun a(exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.ASSERT, null, null, exception)
    }

    override fun a(clazz: Any?, message: String?): ELogContract.Logger = apply {
        applyLog(ELog.Level.ASSERT, clazz.captureTag(), message, null)
    }

    override fun a(clazz: Any?, exception: Throwable): ELogContract.Logger = apply {
        applyLog(ELog.Level.ASSERT, clazz.captureTag(), null, exception)
    }
}