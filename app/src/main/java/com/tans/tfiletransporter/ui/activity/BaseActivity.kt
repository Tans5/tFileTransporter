package com.tans.tfiletransporter.ui.activity

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.di
import org.kodein.di.android.retainedSubDI

abstract class BaseActivity<Binding : ViewDataBinding, State>(
    @LayoutRes
    layoutId: Int,
    defaultState: State
) : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main), Stateable<State> by Stateable(defaultState), BindLife by BindLife(), DIAware {

    protected val binding: Binding by lazy { DataBindingUtil.setContentView(this, layoutId) }

    override val di: DI by retainedSubDI(di()) {  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding
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
        cancel("Activity ${this::class.java.name} Closed.")
        lifeCompositeDisposable.clear()
    }
}