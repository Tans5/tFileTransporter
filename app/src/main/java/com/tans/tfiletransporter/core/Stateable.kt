
package com.tans.tfiletransporter.core

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext

interface Stateable<State : Any> {

    val stateStore: Subject<State>

    fun bindState(): Observable<State> = stateStore

    fun updateState(newState: (State) -> State): Single<State> = stateStore.firstOrError()
        .map(newState)
        .doOnSuccess { state: State -> stateStore.onNext(state) }

    fun updateStateCompletable(newState: (State) -> State): Completable = updateState(newState).ignoreElement()

    fun <T : Any> CoroutineScope.render(mapper: ((State) -> T), handler: suspend (T) -> Unit): Completable = bindState()
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

fun <State : Any> Stateable(defaultState: State) = object : Stateable<State> {
    override val stateStore: Subject<State> = BehaviorSubject.createDefault(defaultState).toSerialized()
}