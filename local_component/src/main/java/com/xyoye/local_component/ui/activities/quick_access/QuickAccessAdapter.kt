package com.xyoye.local_component.ui.activities.quick_access

import android.widget.ImageView
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
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.common_component.weight.dialog.CommonDialog
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
    private val isGridView: Boolean = false
) {

    fun create(): BaseAdapter {
        return if (isGridView) {
            createGridAdapter()
        } else {
            createListAdapter()
        }
    }

    private fun createListAdapter(): BaseAdapter {
        return buildAdapter {
            addEmptyView(R.layout.layout_empty) {
                initEmptyView {
                    itemBinding.emptyTv.text = "暂无快速访问项目\n在文件浏览中添加快速访问后，将在此显示"
                }
            }

            addItem<QuickAccessItem, ItemQuickAccessEntryBinding>(R.layout.item_quick_access_entry) {
                checkType { data -> (data as QuickAccessItem).isDirectory }
                initView { data, _, _ ->
                    itemBinding.run {
                        iconIv.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        iconIv.setImageResource(com.xyoye.common_component.R.drawable.ic_folder)
                        nameTv.text = data.name
                        libraryTv.text = data.libraryDisplayName
                        pathTv.text = data.storagePath
                        itemLayout.setOnClickListener { viewModel.openItem(data) }
                        itemLayout.setOnLongClickListener {
                            showRemoveDialog(data)
                            true
                        }
                    }
                }
            }

            addItem<QuickAccessItem, ItemQuickAccessFileBinding>(R.layout.item_quick_access_file) {
                checkType { data -> !(data as QuickAccessItem).isDirectory }
                initView { data, _, _ ->
                    itemBinding.run {
                        loadFileThumbnail(coverIv, data)
                        nameTv.text = data.name
                        libraryTv.text = data.libraryDisplayName
                        pathTv.text = data.storagePath
                        itemLayout.setOnClickListener { viewModel.openItem(data) }
                        itemLayout.setOnLongClickListener {
                            showRemoveDialog(data)
                            true
                        }
                    }
                }
            }
        }
    }

    private fun createGridAdapter(): BaseAdapter {
        return buildAdapter {
            addEmptyView(R.layout.layout_empty) {
                initEmptyView {
                    itemBinding.emptyTv.text = "暂无快速访问项目\n在文件浏览中添加快速访问后，将在此显示"
                }
            }

            addItem<QuickAccessItem, ItemQuickAccessEntryGridBinding>(R.layout.item_quick_access_entry_grid) {
                checkType { data -> (data as QuickAccessItem).isDirectory }
                initView { data, _, _ ->
                    itemBinding.run {
                        folderIv.setImageResource(com.xyoye.common_component.R.drawable.ic_folder)
                        folderTv.text = data.name
                        libraryTv.text = data.libraryDisplayName
                        itemLayout.setOnClickListener { viewModel.openItem(data) }
                        itemLayout.setOnLongClickListener {
                            showRemoveDialog(data)
                            true
                        }
                    }
                }
            }

            addItem<QuickAccessItem, ItemQuickAccessFileGridBinding>(R.layout.item_quick_access_file_grid) {
                checkType { data -> !(data as QuickAccessItem).isDirectory }
                initView { data, _, _ ->
                    itemBinding.run {
                        loadFileThumbnail(coverIv, data)
                        titleTv.text = data.name
                        libraryTv.text = data.libraryDisplayName
                        mainActionFl.setOnClickListener { viewModel.openItem(data) }
                        mainActionFl.setOnLongClickListener {
                            showRemoveDialog(data)
                            true
                        }
                    }
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
        val coverSource = ThumbnailMemoryCache.getCoverPath(uniqueKey)
            ?: uniqueKey.toCoverFile()?.takeIf { it.isInvalid().not() }?.absolutePath

        if (coverSource != null) {
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
            imageView.setImageResource(com.xyoye.common_component.R.drawable.ic_video_cover)
        }
    }

    private fun showRemoveDialog(item: QuickAccessItem) {
        CommonDialog.Builder(activity)
            .apply {
                content = "确认移除快速访问项目?\n\n${item.name}"
                positiveText = "确认"
                addPositive { dialog ->
                    dialog.dismiss()
                    viewModel.removeItem(item)
                }
                addNegative()
            }.build().show()
    }
}