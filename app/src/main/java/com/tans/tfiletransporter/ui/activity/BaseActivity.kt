package com.tans.tfiletransporter.ui.activity

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.kodein.di.*
import org.kodein.di.android.di
import org.kodein.di.android.retainedSubDI

abstract class BaseActivity<Binding : ViewDataBinding, State>(
    @LayoutRes
    layoutId: Int,
    val defaultState: State
) : AppCompatActivity(), CoroutineScope by MainScope(), Stateable<State>, BindLife by BindLife(), DIAware {

    protected val binding: Binding by lazy { DataBindingUtil.setContentView(this, layoutId) }

//    override val di: DI by retainedSubDI(di()) {
//        bind<Subject<State>>() with singleton { BehaviorSubject.createDefault(defaultState).toSerialized() }
//        addDIInstance()
//    }
//
//    override val stateStore: Subject<State> by instance()

//    open fun DI.MainBuilder.addDIInstance() {
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            firstLaunchInitData()
        }
        initViews(binding)
    }

    open fun firstLaunchInitData() {

    }

    open fun initViews(binding: Binding) {

    }

    override fun onBackPressed() {
        for (f in supportFragmentManager.fragments) {
            if (!f.isHidden && f is BaseFragment<*, *> && f.onBackPressed()) {
                return
            }
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifeCompositeDisposable.clear()
    }
}