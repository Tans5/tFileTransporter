package com.tans.tfiletransporter
import kotlinx.coroutines.CancellableContinuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun <T> CancellableContinuation<T>.resumeIfActive(t: T) {
    if (isActive) {
        this.resume(t)
    }
}

fun <T> CancellableContinuation<T>.resumeExceptionIfActive(e: Throwable) {
    if (isActive) {
        this.resumeWithException(e)
    }
}