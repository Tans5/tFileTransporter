package com.tans.tfiletransporter.ui

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

abstract class BaseActivity<Binding : ViewDataBinding, State>(
    @LayoutRes
    layoutId: Int,
    defaultState: State
) : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main), Stateable<State> by Stateable(defaultState), BindLife by BindLife() {

    protected val binding: Binding by lazy { DataBindingUtil.setContentView(this, layoutId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel("Activity ${this::class.java.name} Closed.")
        lifeCompositeDisposable.clear()
    }
}