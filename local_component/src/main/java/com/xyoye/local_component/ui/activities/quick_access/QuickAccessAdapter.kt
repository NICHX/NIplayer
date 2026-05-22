package com.xyoye.local_component.ui.activities.quick_access

import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.xyoye.common_component.adapter.BaseAdapter
import com.xyoye.common_component.adapter.addEmptyView
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.extension.isInvalid
import com.xyoye.common_component.extension.toAudioCoverFile
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.data_component.bean.QuickAccessItem
import com.xyoye.local_component.R
import com.xyoye.local_component.databinding.ItemQuickAccessEntryBinding
import com.xyoye.local_component.databinding.ItemQuickAccessEntryGridBinding
import com.xyoye.local_component.databinding.ItemQuickAccessFileBinding
import com.xyoye.local_component.databinding.ItemQuickAccessFileGridBinding
import java.io.File

class QuickAccessAdapter(
    private val activity: QuickAccessActivity,
    private val viewModel: QuickAccessViewModel,
    private val isGridView: Boolean
) {

    private var onItemSelectedListener: ((position: Int) -> Unit)? = null

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    fun create(): BaseAdapter {
        return if (isGridView) {
            createGridAdapter()
        } else {
            createListAdapter()
        }
    }

    private fun updateItemBackground(view: View, position: Int) {
        if (!viewModel.isEditing) {
            view.background = null
            return
        }
        view.background = if (viewModel.isItemSelected(position)) {
            ColorDrawable(ContextCompat.getColor(view.context, R.color.colorAccentDim))
        } else {
            null
        }
    }

    private fun onItemClick(position: Int) {
        if (viewModel.isEditing) {
            viewModel.toggleItemSelection(position)
            onItemSelectedListener?.invoke(position)
        } else {
            val items = (viewModel.quickAccessLiveData.value ?: return)
            if (position < items.size) {
                viewModel.openItem(items[position])
            }
        }
    }

    private fun onItemLongClick(position: Int): Boolean {
        if (viewModel.isEditing) return false
        val items = viewModel.quickAccessLiveData.value ?: return false
        if (position >= items.size) return false
        showRemoveDialog(items[position])
        return true
    }

    private fun showRemoveDialog(item: QuickAccessItem) {
        com.xyoye.common_component.weight.dialog.CommonDialog.Builder(activity)
            .apply {
                content = "确认取消收藏 ${item.name}?"
                positiveText = "确认"
                addPositive { dialog ->
                    dialog.dismiss()
                    viewModel.removeItem(item)
                }
                addNegative()
            }.build().show()
    }

    private fun createListAdapter() = buildAdapter {
        addEmptyView(R.layout.layout_empty) {
            initEmptyView {
                itemBinding.emptyTv.text = "暂无快速访问项目\n在文件浏览中添加快速访问后，将在此显示"
            }
        }

        addItem<QuickAccessItem, ItemQuickAccessEntryBinding>(R.layout.item_quick_access_entry) {
            checkType { data -> (data as QuickAccessItem).isDirectory }
            initView { data, position, _ ->
                val pos = position
                itemBinding.run {
                    iconIv.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    iconIv.setImageResource(com.xyoye.common_component.R.drawable.ic_folder)
                    nameTv.text = data.name
                    libraryTv.text = data.libraryDisplayName
                    pathTv.text = data.storagePath
                    updateItemBackground(itemLayout, pos)
                    itemLayout.setOnClickListener { onItemClick(pos) }
                    itemLayout.setOnLongClickListener { onItemLongClick(pos) }
                }
            }
        }

        addItem<QuickAccessItem, ItemQuickAccessFileBinding>(R.layout.item_quick_access_file) {
            checkType { data -> !(data as QuickAccessItem).isDirectory }
            initView { data, position, _ ->
                val pos = position
                itemBinding.run {
                    loadFileThumbnail(coverIv, data)
                    nameTv.text = data.name
                    libraryTv.text = data.libraryDisplayName
                    pathTv.text = data.storagePath
                    updateItemBackground(itemLayout, pos)
                    itemLayout.setOnClickListener { onItemClick(pos) }
                    itemLayout.setOnLongClickListener { onItemLongClick(pos) }
                }
            }
        }
    }

    private fun createGridAdapter() = buildAdapter {
        addEmptyView(R.layout.layout_empty) {
            initEmptyView {
                itemBinding.emptyTv.text = "暂无快速访问项目\n在文件浏览中添加快速访问后，将在此显示"
            }
        }

        addItem<QuickAccessItem, ItemQuickAccessEntryGridBinding>(R.layout.item_quick_access_entry_grid) {
            checkType { data -> (data as QuickAccessItem).isDirectory }
            initView { data, position, _ ->
                val pos = position
                itemBinding.run {
                    folderIv.setImageResource(com.xyoye.common_component.R.drawable.ic_folder)
                    folderTv.text = data.name
                    libraryTv.text = data.libraryDisplayName
                    updateItemBackground(itemLayout, pos)
                    itemLayout.setOnClickListener { onItemClick(pos) }
                    itemLayout.setOnLongClickListener { onItemLongClick(pos) }
                }
            }
        }

        addItem<QuickAccessItem, ItemQuickAccessFileGridBinding>(R.layout.item_quick_access_file_grid) {
            checkType { data -> !(data as QuickAccessItem).isDirectory }
            initView { data, position, _ ->
                val pos = position
                itemBinding.run {
                    loadFileThumbnail(coverIv, data)
                    titleTv.text = data.name
                    libraryTv.text = data.libraryDisplayName
                    updateItemBackground(itemLayout, pos)
                    itemLayout.setOnClickListener { onItemClick(pos) }
                    itemLayout.setOnLongClickListener { onItemLongClick(pos) }
                }
            }
        }
    }

    private fun loadFileThumbnail(imageView: ImageView, item: QuickAccessItem) {
        val uniqueKey = if (item.uniqueKey.isNotEmpty()) {
            item.uniqueKey
        } else {
            "${item.libraryId}-${item.storagePath}".toMd5String()
        }

        val isAudio = com.xyoye.common_component.utils.isAudioFile(item.name)
        val coverFile = if (isAudio) {
            uniqueKey.toAudioCoverFile()
        } else {
            uniqueKey.toCoverFile()
        }
        val coverSource = ThumbnailMemoryCache.getCoverPath(uniqueKey)
            ?: coverFile?.takeIf { it.isInvalid().not() }?.absolutePath

        if (coverSource != null) {
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(imageView)
                .load(coverSource)
                .apply(RequestOptions().apply {
                    centerCrop()
                    dontAnimate()
                    diskCacheStrategy(DiskCacheStrategy.NONE)
                    skipMemoryCache(false)
                    signature(ObjectKey(File(coverSource).lastModified()))
                    format(DecodeFormat.PREFER_RGB_565)
                })
                .into(imageView)
        } else {
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(
                when {
                    com.xyoye.common_component.utils.isVideoFile(item.name) -> com.xyoye.common_component.R.drawable.ic_video
                    com.xyoye.common_component.utils.isAudioFile(item.name) -> com.xyoye.common_component.R.drawable.ic_file_audio
                    com.xyoye.common_component.utils.isImageFile(item.name) -> com.xyoye.common_component.R.drawable.ic_image
                    else -> com.xyoye.common_component.R.drawable.ic_video
                }
            )
        }
    }
}
