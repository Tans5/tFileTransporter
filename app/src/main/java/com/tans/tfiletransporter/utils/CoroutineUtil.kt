package com.tans.tfiletransporter.utils

import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.*

fun CoroutineScope.launchHandleError(context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> Unit): Job {
    return launch(
            context = CoroutineExceptionHandler { _, throwable -> Log.e("Coroutine Launch", throwable.stackTraceToString()) } + context,
            start = start,
            block = block
    )
}

suspend fun <T> blockToSuspend(blockContext: CoroutineContext = Dispatchers.IO,
                               cancel: () -> Unit = {},
                               block: () -> T): T = suspendCancellableCoroutine { cont ->
    val interceptor = cont.context[ContinuationInterceptor]
    val blockInterceptor = blockContext[ContinuationInterceptor]
    if (interceptor is CoroutineDispatcher && blockInterceptor is CoroutineDispatcher) {
        blockInterceptor.dispatch(cont.context, Runnable {
            try {
                val result = block()
                interceptor.dispatch(cont.context, Runnable { if (cont.isActive) cont.resume(result) })
            } catch (e: Throwable) {
                interceptor.dispatch(cont.context, Runnable { if (cont.isActive) cont.resumeWithException(e) })
            }
        })
    } else {
        cont.resumeWithException(Throwable("Can't find ContinuaDispatcher"))
    }
    cont.invokeOnCancellation { cancel() }
}