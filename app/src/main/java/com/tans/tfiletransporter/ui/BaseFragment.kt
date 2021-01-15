package com.tans.tfiletransporter.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

abstract class BaseFragment<Binding: ViewDataBinding, State>(
    @LayoutRes
    val layoutId: Int,
    default: State
) : Fragment(), Stateable<State> by Stateable(default), BindLife by BindLife(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    lateinit var binding: Binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        lifeCompositeDisposable.clear()
    }
}