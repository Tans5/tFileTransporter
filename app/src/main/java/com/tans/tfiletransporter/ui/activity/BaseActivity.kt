package com.tans.tfiletransporter.ui.activity

import android.os.Bundle
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.kodein.di.*
import org.kodein.di.android.di
import org.kodein.di.android.retainedSubDI
import kotlin.coroutines.CoroutineContext

abstract class BaseActivity<Binding : ViewDataBinding, State>(
    @LayoutRes
    layoutId: Int,
    val defaultState: State
) : AppCompatActivity(), CoroutineScope, Stateable<State>, BindLife by BindLife(), DIAware {

    class ActivityViewModel<State>(defaultState: State) : ViewModel(),
        BindLife by BindLife(),
        CoroutineScope by CoroutineScope(Dispatchers.Main),
        Stateable<State> by Stateable(defaultState) {

        fun clearRxLife() {
            lifeCompositeDisposable.clear()
        }

        override fun onCleared() {
            super.onCleared()
            lifeCompositeDisposable.clear()
            cancel("Activity Finish")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val viewModel: ActivityViewModel<State> by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory{
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActivityViewModel(defaultState) as T
            }
        })[ActivityViewModel::class.java] as ActivityViewModel<State>
    }

    override val coroutineContext: CoroutineContext by lazy { viewModel.coroutineContext }

    override val stateStore: Subject<State> by lazy { viewModel.stateStore }

    protected val binding: Binding by lazy { DataBindingUtil.setContentView(this, layoutId) }

    override val di: DI by retainedSubDI(di()) {
        bind<OnBackPressedDispatcher>() with singleton { onBackPressedDispatcher }
        addDIInstance()
    }

    open fun DI.MainBuilder.addDIInstance() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.clearRxLife()
        if (savedInstanceState == null) {
            firstLaunchInitData()
        }
        initViews(binding)
        onBackPressedDispatcher.addCallback {
            onActivityBackPressed()
        }
    }

    open fun firstLaunchInitData() {

    }

    open fun initViews(binding: Binding) {

    }

    open fun onActivityBackPressed() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifeCompositeDisposable.clear()
    }
}