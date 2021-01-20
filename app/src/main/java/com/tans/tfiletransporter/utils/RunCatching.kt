package com.tans.tfiletransporter.utils

sealed class Result<T> {

    abstract fun isFailure(): Boolean
    abstract fun isSuccess(): Boolean
    abstract fun resultOrNull(): T?
    abstract fun errorOrNull(): Throwable?

    class Success<T>(private val result: T) : Result<T>() {
        override fun isFailure(): Boolean = false

        override fun isSuccess(): Boolean = true

        override fun resultOrNull(): T? = result

        override fun errorOrNull(): Throwable? = null

    }

    class Failure<T>(private val error: Throwable) : Result<T>() {
        override fun isFailure(): Boolean = true

        override fun isSuccess(): Boolean = false

        override fun resultOrNull(): T? = null

        override fun errorOrNull(): Throwable? = error
    }

    companion object {
        fun <T> success(data: T): Success<T> = Success(data)

        fun <T> failure(e: Throwable): Failure<T> = Failure(e)
    }
}

fun <T, R> Result<T>.map(f: Unit.(T) -> Result<R>): Result<R> = map(Unit, f)

inline fun <T, R, Receive> Result<T>.map(receive: Receive, f: Receive.(T) -> Result<R>): Result<R> {
    return if (isSuccess()) {
        receive.f(resultOrNull()!!)
    } else {
        Result.failure(errorOrNull()!!)
    }
}

inline fun <T, R> T.runCatching(block: T.() -> R): Result<R> = try {
    Result.success(block())
} catch (e: Throwable) {
    Result.failure(e)
}