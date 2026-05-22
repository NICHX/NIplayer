package com.xyoye.common_component.weight.dialog

import android.app.Activity
import android.text.method.PasswordTransformationMethod
import com.xyoye.common_component.R
import com.xyoye.common_component.databinding.DialogCommonEditDoubleBinding
import com.xyoye.common_component.utils.hideKeyboard
import com.xyoye.common_component.utils.showKeyboard

class MusicMetadataEditDialog(
    activity: Activity,
    private val apiUrl: String?,
    private val apiAuth: String?,
    private val callback: (apiUrl: String, apiAuth: String) -> Unit
) : BaseBottomDialog<DialogCommonEditDoubleBinding>(activity) {

    private lateinit var binding: DialogCommonEditDoubleBinding
    private var isPasswordVisible = false

    override fun getChildLayoutId() = R.layout.dialog_common_edit_double

    override fun initView(binding: DialogCommonEditDoubleBinding) {
        this.binding = binding

        setTitle("设置音乐元数据API")
        setPositiveText("保存")
        setNegativeText("取消")

        binding.inputEtApiUrl.setText(apiUrl ?: "")
        binding.inputEtApiAuth.setText(apiAuth ?: "")

        binding.btnTogglePassword.setOnClickListener {
            togglePasswordVisibility()
        }

        setPositiveListener {
            val url = binding.inputEtApiUrl.text.toString().trim()
            val auth = binding.inputEtApiAuth.text.toString().trim()
            callback(url, auth)
            dismiss()
        }

        setNegativeListener {
            dismiss()
        }

        binding.inputEtApiUrl.postDelayed({
            binding.inputEtApiUrl.requestFocus()
            showKeyboard(binding.inputEtApiUrl)
        }, 300)
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        if (isPasswordVisible) {
            binding.inputEtApiAuth.transformationMethod = null
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_open)
            binding.btnTogglePassword.contentDescription = "隐藏密码"
        } else {
            binding.inputEtApiAuth.transformationMethod = PasswordTransformationMethod.getInstance()
            binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_close)
            binding.btnTogglePassword.contentDescription = "显示密码"
        }
        binding.inputEtApiAuth.setSelection(binding.inputEtApiAuth.text?.length ?: 0)
    }

    override fun dismiss() {
        hideKeyboard(binding.inputEtApiUrl)
        hideKeyboard(binding.inputEtApiAuth)
        super.dismiss()
    }
}
