package com.tans.tfiletransporter.ui.activity

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
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.subDI
import org.kodein.di.android.x.di

abstract class BaseFragment<Binding: ViewDataBinding, State>(
    @LayoutRes
    val layoutId: Int,
    default: State
) : Fragment(), Stateable<State> by Stateable(default), BindLife by BindLife(), CoroutineScope by CoroutineScope(Dispatchers.Main), DIAware {

    lateinit var binding: Binding

    override val di: DI by subDI(di()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    var rootView: View? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = this.rootView
        return if (rootView == null) {
            binding = DataBindingUtil.inflate(inflater, layoutId, container, false)
            this.rootView = binding.root
            binding.root
        } else {
            rootView
        }
    }


    var isInit = false
    override fun onResume() {
        super.onResume()
        if (!isInit) {
            initViews(binding)
            isInit = true
        }
    }

    open fun initViews(binding: Binding) {

    }

    open fun onBackPressed(): Boolean = false

    override fun onDestroy() {
        super.onDestroy()
        cancel()
        lifeCompositeDisposable.clear()
    }
}