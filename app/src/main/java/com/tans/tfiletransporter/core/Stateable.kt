
package com.tans.tfiletransporter.core

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext

interface Stateable<State> {

    val stateStore: Subject<State>

    fun bindState(): Observable<State> = stateStore

    fun updateState(newState: (State) -> State): Single<State> = stateStore.firstOrError()
        .map(newState)
        .doOnSuccess { state: State -> if (state != null) stateStore.onNext(state) }

    fun updateStateCompletable(newState: (State) -> State): Completable = updateState(newState).ignoreElement()

    fun <T> CoroutineScope.render(mapper: ((State) -> T), handler: suspend (T) -> Unit): Completable = bindState()
        .map(mapper)
        .distinctUntilChanged()
        .switchMapSingle { t ->
            rxSingle { withContext(Dispatchers.Main) { handler(t) } }
        }
        .ignoreElements()

    fun CoroutineScope.render(handler: suspend (State) -> Unit): Completable = bindState()
        .distinctUntilChanged()
        .switchMapSingle { t ->
            rxSingle { withContext(Dispatchers.Main) { handler(t) } }
        }
        .ignoreElements()
}

fun <State> Stateable(defaultState: State) = object : Stateable<State> {
    override val stateStore: Subject<State> = BehaviorSubject.createDefault(defaultState).toSerialized()
}