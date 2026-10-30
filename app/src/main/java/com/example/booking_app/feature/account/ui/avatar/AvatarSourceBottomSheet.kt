package com.example.booking_app.feature.account.ui.avatar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.booking_app.databinding.SheetAccountAvatarSourceBinding
import com.example.booking_app.feature.account.ui.AccountResults
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * "Chụp ảnh" / "Chọn từ thư viện". Chỉ trả lựa chọn; `EditProfileFragment` giữ các launcher (quyền camera, Photo Picker)
 * vì sheet đóng ngay sau khi chọn.
 */
class AvatarSourceBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SheetAccountAvatarSourceBinding.inflate(inflater, container, false)
        binding.cameraOption.setOnClickListener { choose(AccountResults.SOURCE_CAMERA) }
        binding.galleryOption.setOnClickListener { choose(AccountResults.SOURCE_GALLERY) }
        return binding.root
    }

    private fun choose(source: String) {
        setFragmentResult(AccountResults.AVATAR_SOURCE, bundleOf(AccountResults.SOURCE to source))
        dismiss()
    }
}
