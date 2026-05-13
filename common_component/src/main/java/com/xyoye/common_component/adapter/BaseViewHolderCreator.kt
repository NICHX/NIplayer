package com.xyoye.common_component.adapter

import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding

/**
 * Created by xyoye on 2020/7/7.
 */

abstract class BaseViewHolderCreator<V : ViewDataBinding> {
    abstract fun isForViewType(data: Any?, position: Int): Boolean

    abstract fun getResourceId(): Int

    abstract fun onBindViewHolder(
        data: Any?,
        position: Int,
        creator: BaseViewHolderCreator<out ViewDataBinding>
    )

    open fun onBindViewHolderWithPayloads(
        data: Any?,
        position: Int,
        creator: BaseViewHolderCreator<out ViewDataBinding>,
        payloads: MutableList<Any>
    ) {
        onBindViewHolder(data, position, creator)
    }

    lateinit var itemBinding: V

    fun initItemBinding(itemView: View) {
        this.itemBinding = DataBindingUtil.getBinding(itemView)!!
    }
}