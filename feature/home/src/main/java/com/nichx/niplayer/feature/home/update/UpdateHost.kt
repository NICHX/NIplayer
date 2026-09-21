package com.nichx.niplayer.feature.home.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * 版本更新宿主（**A2 架构修复**）。
 *
 * 原先由 `:app` 的 `MainActivity` 自行 `hiltViewModel<UpdateViewModel>()` 并调用
 * `checkUpdate(auto = true)`，宿主因此必须 import 本模块的 `UpdateViewModel` / `UpdateDialogHost`。
 * 现封装为一个自持 Composable，宿主只需调用 [UpdateHost]。
 *
 * 启动自动检查（24h 节流，静默失败），有更新时弹窗提示。
 */
@Composable
fun UpdateHost() {
    val updateViewModel: UpdateViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        updateViewModel.checkUpdate(auto = true)
    }
    UpdateDialogHost(viewModel = updateViewModel)
}
