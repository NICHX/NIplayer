# 文件浏览性能优化文档

## 问题描述

在文件浏览过程中，从上往下滚动（加载新内容）时明显卡顿，但从下往上滚动（复用已加载的内容）时相对流畅。

## 问题根因分析

### 1. 缩略图生成与加载
- 向下滚动时，不断有新文件进入视野，触发缩略图生成请求
- `ThumbnailGeneratorManager` 在滚动时持续处理缩略图生成，占用 CPU 资源
- 缩略图生成完成后会调用 `notifyItemChanged` 导致 UI 更新

### 2. Item 入场动画
- `StorageFileAdapter` 使用 `setupVerticalAnimation()` 为新出现的 Item 添加入场动画
- 动画在快速滚动时会增加渲染负担

### 3. 嵌套 RecyclerView 重复创建
- 每个视频 Item 内部有一个显示标签的 RecyclerView
- 每次绑定 View 时都重新创建 LayoutManager 和 Adapter

### 4. 滚动时的持续处理
- 滚动监听器在滚动状态变化时不断触发 `reprioritize`
- 即使不显示缩略图，后台仍在持续生成

## 优化方案

### 1. 移除 Item 入场动画

**文件**: `storage_component/src/main/java/com/xyoye/storage_component/ui/fragment/storage_file/StorageFileAdapter.kt`

**修改**:
- 在 `createListAdapter()` 和 `createGridAdapter()` 中移除 `setupVerticalAnimation()` 调用
- 避免滚动时新 Item 的入场动画造成卡顿

### 2. 滚动时暂停缩略图生成

**文件**: 
- `storage_component/src/main/java/com/xyoye/storage_component/ui/fragment/storage_file/StorageFileFragment.kt`
- `common_component/src/main/java/com/xyoye/common_component/utils/ThumbnailGeneratorManager.kt`

**修改**:
- 在 `ThumbnailGeneratorManager` 中添加 `pauseGenerateThumbnails()` 和 `resumeGenerateThumbnails()` 方法
- 在滚动开始时（`SCROLL_STATE_DRAGGING`）暂停缩略图生成
- 在滚动停止时（`SCROLL_STATE_IDLE`）恢复缩略图生成
- 增加 `isPaused` 标志，在 `processNextBatch()` 中检查此标志

### 3. 增加 RecyclerView 缓存

**文件**: `storage_component/src/main/java/com/xyoye/storage_component/ui/fragment/storage_file/StorageFileFragment.kt`

**修改**:
- 调用 `setItemViewCacheSize(20)` 增加缓存数量
- 提高 ViewHolder 复用率，减少创建开销

### 4. 优化嵌套 RecyclerView

**文件**: `storage_component/src/main/java/com/xyoye/storage_component/ui/fragment/storage_file/StorageFileAdapter.kt`

**修改**:
- 在 `setupVideoTag()` 中检查 adapter 是否已存在
- 只在第一次绑定时创建 LayoutManager 和 Adapter
- 后续只调用 `setData()` 更新数据

## 技术细节

### ThumbnailGeneratorManager 暂停机制
```kotlin
private var isPaused = false

fun pauseGenerateThumbnails() {
    isPaused = true
}

fun resumeGenerateThumbnails() {
    isPaused = false
    if (!isProcessing && pendingFiles.isNotEmpty()) {
        processNextBatch()
    }
}

private fun processNextBatch() {
    if (isProcessing || pendingFiles.isEmpty() || isPaused) {
        return
    }
    // ... 处理逻辑
}
```

### StorageFileFragment 滚动监听
```kotlin
addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        when (newState) {
            RecyclerView.SCROLL_STATE_IDLE -> {
                // 停止滚动时恢复
                ThumbnailGeneratorManager.resumeGenerateThumbnails()
                // ... 继续处理
            }
            RecyclerView.SCROLL_STATE_DRAGGING -> {
                // 开始拖动时暂停
                ThumbnailGeneratorManager.pauseGenerateThumbnails()
            }
        }
    }
})
```

### 嵌套 RecyclerView 优化
```kotlin
private fun setupVideoTag(tagRv: RecyclerView, data: StorageFile) {
    if (tagRv.adapter == null) {
        // 只在第一次时创建 adapter
        tagRv.apply {
            layoutManager = horizontal()
            adapter = buildAdapter { ... }
            addItemDecoration(tagDecoration)
        }
    }
    // 每次只更新数据
    tagRv.adapter?.setData(generateVideoTags(data))
}
```

## 效果预期

1. **滚动流畅度大幅提升** - 滚动时暂停缩略图生成，减少 CPU 占用
2. **内存占用降低** - 增加缓存减少对象创建，避免重复创建嵌套 RecyclerView
3. **用户体验改善** - 移除不必要的动画，滚动更加丝滑
4. **功能完整性保持** - 缩略图生成仅在滚动停止时进行，不影响功能

## 其他可考虑的优化建议

1. **进一步优化缩略图生成策略**
   - 可以考虑降低缩略图生成的批次大小（BATCH_SIZE）
   - 或者降低缩略图的质量/尺寸

2. **增加加载优先级**
   - 在可见区域的文件优先生成缩略图
   - 不可见区域延后处理

3. **使用预加载机制**
   - 根据滚动方向预判可能出现的文件
   - 提前开始生成这些文件的缩略图

4. **RecyclerView Pool**
   - 对于网格布局，可以考虑使用 RecyclerViewPool 进一步优化复用
