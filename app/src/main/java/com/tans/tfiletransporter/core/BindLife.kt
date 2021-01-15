package com.tans.tfiletransporter.core

import android.util.Log
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

interface BindLife {
    val lifeCompositeDisposable: CompositeDisposable

    fun <T> Observable<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            Log.d(this@BindLife.javaClass.name, "Next: ${it.toString()}")
        }, {
            Log.e(this@BindLife.javaClass.name, it.toString())
        }, {
            Log.d(this@BindLife.javaClass.name,"Complete")
        }))
    }

    fun Completable.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            Log.d(this@BindLife.javaClass.name,"Complete")
        }, {
            Log.d(this@BindLife.javaClass.name, it.toString())
        }))
    }

    fun <T> Single<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe({
            Log.d(this@BindLife.javaClass.name, it.toString())
        }, {
            Log.d(this@BindLife.javaClass.name, it.toString())
        }))
    }

    fun <T> Maybe<T>.bindLife() {
        lifeCompositeDisposable.add(this.subscribe ({
            Log.d(this@BindLife.javaClass.name,"Success: $it")
        }, {
            Log.d(this@BindLife.javaClass.name, it.toString())
        }, {
            Log.d(this@BindLife.javaClass.name,"Complete")
        }))
    }
}

fun BindLife(): com.tans.tfiletransporter.core.BindLife = object : BindLife {
    override val lifeCompositeDisposable: CompositeDisposable = CompositeDisposable()
}