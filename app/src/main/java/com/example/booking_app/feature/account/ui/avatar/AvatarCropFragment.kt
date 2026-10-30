package com.example.booking_app.feature.account.ui.avatar

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.canhub.cropper.CropImageView
import com.example.booking_app.R
import com.example.booking_app.databinding.FragmentAccountAvatarCropBinding
import com.example.booking_app.feature.account.data.ImageProcessor
import com.example.booking_app.feature.account.domain.validation.AvatarImageRule
import com.example.booking_app.feature.account.ui.AccountResults
import com.google.android.material.snackbar.Snackbar
import java.io.File

/**
 * Cắt ảnh vuông bằng `CropImageView` (com.vanniktech:android-image-cropper, plan Complexity Tracking). Ảnh cắt
 * (tối đa 512 px) ghi vào `cacheDir/avatar/` rồi trả URI cho `EditProfileFragment`, nơi ảnh được lưu ngay.
 */
class AvatarCropFragment : Fragment(R.layout.fragment_account_avatar_crop) {

    private val args: AvatarCropFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentAccountAvatarCropBinding.bind(view)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.cropView.setOnSetImageUriCompleteListener { _, _, error ->
            if (error == null) {
                binding.confirmButton.isEnabled = true
            } else {
                showError(binding, R.string.account_error_image_type)
            }
        }
        binding.cropView.setOnCropImageCompleteListener { _, result ->
            setBusy(binding, false)
            val uri = result.uriContent
            if (result.isSuccessful && uri != null) {
                setFragmentResult(AccountResults.AVATAR_CROP, bundleOf(AccountResults.CROPPED_URI to uri.toString()))
                findNavController().popBackStack()
            } else {
                showError(binding, R.string.account_error_generic)
            }
        }
        binding.confirmButton.setOnClickListener {
            setBusy(binding, true)
            val output = File(ImageProcessor.tempDir(requireContext()), "crop_${System.currentTimeMillis()}.jpg")
            binding.cropView.croppedImageAsync(
                saveCompressFormat = Bitmap.CompressFormat.JPEG,
                saveCompressQuality = CROP_QUALITY,
                reqWidth = AvatarImageRule.MAX_DIMENSION,
                reqHeight = AvatarImageRule.MAX_DIMENSION,
                options = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                customOutputUri = Uri.fromFile(output),
            )
        }
        // CropImageView tự lưu ảnh và khung cắt khi xoay màn hình.
        if (savedInstanceState == null) binding.cropView.setImageUriAsync(args.sourceUri.toUri())
    }

    private fun setBusy(binding: FragmentAccountAvatarCropBinding, busy: Boolean) {
        binding.confirmButton.isEnabled = !busy
        binding.confirmButton.text = if (busy) "" else getString(R.string.account_avatar_crop_confirm)
        binding.progress.isVisible = busy
    }

    private fun showError(binding: FragmentAccountAvatarCropBinding, message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private companion object {
        /** Ảnh cắt được nén lại ở `ImageProcessor` (85 trở xuống), nên lần này giữ chất lượng cao. */
        const val CROP_QUALITY = 95
    }
}
