package com.xyoye.common_component.weight

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.xyoye.common_component.R

class ExpandableFabMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val actionContainer: LinearLayout
    private val mainFab: FloatingActionButton

    var isExpanded = false
        private set

    private val actions = mutableListOf<FabAction>()
    private var collapsedIconRes = R.drawable.ic_menu_white

    data class FabAction(
        val id: Int,
        val icon: Int,
        val label: String,
        val onClick: () -> Unit,
    )

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_expandable_fab, this, true)
        actionContainer = findViewById(R.id.action_container)
        mainFab = findViewById(R.id.main_fab)

        mainFab.setOnClickListener { toggle() }
    }

    fun addAction(action: FabAction) {
        actions.add(action)
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.item_expandable_fab_action, actionContainer, false)

        val labelTv = itemView.findViewById<TextView>(R.id.label_tv)
        val actionFab = itemView.findViewById<FloatingActionButton>(R.id.action_fab)

        itemView.tag = action.id
        updateActionItem(itemView, action)

        itemView.setOnClickListener {
            action.onClick()
            collapse()
        }
        actionFab.setOnClickListener {
            action.onClick()
            collapse()
        }

        itemView.alpha = 0f
        actionContainer.addView(itemView)
    }

    fun updateAction(actionId: Int, icon: Int, label: String) {
        val index = actions.indexOfFirst { it.id == actionId }
        if (index == -1) return
        actions[index] = actions[index].copy(icon = icon, label = label)
        for (i in 0 until actionContainer.childCount) {
            val child = actionContainer.getChildAt(i)
            if (child.tag == actionId) {
                updateActionItem(child, actions[index])
                break
            }
        }
    }

    private fun updateActionItem(itemView: View, action: FabAction) {
        val labelTv = itemView.findViewById<TextView>(R.id.label_tv)
        val actionFab = itemView.findViewById<FloatingActionButton>(R.id.action_fab)
        labelTv.text = action.label
        actionFab.setImageResource(action.icon)
    }

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        actionContainer.visibility = View.VISIBLE
        mainFab.setImageResource(R.drawable.ic_close_white)

        for (i in 0 until actionContainer.childCount) {
            val child = actionContainer.getChildAt(i)
            child.animate().cancel()
            child.clearAnimation()
            child.alpha = 0f
            child.animate()
                .alpha(1f)
                .setDuration(200)
                .setStartDelay((i * 50).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        mainFab.setImageResource(collapsedIconRes)

        val childCount = actionContainer.childCount
        if (childCount == 0) {
            actionContainer.visibility = View.GONE
            return
        }

        for (i in childCount - 1 downTo 0) {
            val child = actionContainer.getChildAt(i)
            child.animate().cancel()
            child.clearAnimation()
            child.animate()
                .alpha(0f)
                .setDuration(150)
                .setStartDelay(((childCount - 1 - i) * 30).toLong())
                .setInterpolator(DecelerateInterpolator())
                .setListener(if (i == 0) object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isExpanded) {
                            actionContainer.visibility = View.GONE
                        }
                    }
                } else null)
                .start()
        }
    }

    fun setMainFabIcon(iconRes: Int) {
        mainFab.setImageResource(iconRes)
    }

    fun setCollapsedIcon(iconRes: Int) {
        collapsedIconRes = iconRes
    }
}
