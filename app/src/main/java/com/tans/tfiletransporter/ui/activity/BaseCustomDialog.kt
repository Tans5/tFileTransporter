package com.tans.tfiletransporter.ui.activity

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.databinding.BaseDialogLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.closestDI

abstract class BaseCustomDialog<Binding: ViewDataBinding, State : Any>(context: Context, @LayoutRes val layoutId: Int, defaultState: State, private val clearBackground: Boolean = false, private val outSizeCancelable: Boolean = true)
    : AppCompatDialog(context), BindLife by BindLife(), DIAware, CoroutineScope by CoroutineScope(Dispatchers.Main), Stateable<State> by Stateable(defaultState) {

    override val di: DI by closestDI()

    val baseDialogBinding: BaseDialogLayoutBinding by lazy {
        DataBindingUtil.inflate<BaseDialogLayoutBinding>(LayoutInflater.from(context), R.layout.base_dialog_layout, window?.decorView as? ViewGroup, false)
    }

    val binding: Binding by lazy {
        DataBindingUtil.inflate<Binding>(LayoutInflater.from(context), layoutId, baseDialogBinding.outsideLayout, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding.root.isClickable = true
        adjustContentView(binding.root)
        val lp = FrameLayout.LayoutParams(binding.root.layoutParams as ViewGroup.MarginLayoutParams)
        lp.gravity = Gravity.CENTER
        baseDialogBinding.outsideLayout.addView(binding.root, lp)
        setContentView(baseDialogBinding.root)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (clearBackground) {
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
    }

    open fun adjustContentView(content: View): View = content

    override fun onStart() {
        super.onStart()
        setCanceledOnTouchOutside(false)
        baseDialogBinding.outsideLayout.clicks()
            .doOnNext {
                if (outSizeCancelable) {
                    cancel()
                }
            }
            .bindLife()
        bindingStart(binding)
    }

    open fun bindingStart(binding: Binding) {}

    override fun onStop() {
        super.onStop()
        bindingStop(binding)
        cancel("${this::class.java} closed")
        lifeCompositeDisposable.clear()
    }

    open fun bindingStop(binding: Binding) {}

}
