package com.example.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val isLoadingProfile: Boolean = true,
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val savedSuccess: Boolean = false,
    val photoUploadedSuccess: Boolean = false,
    val error: AppError? = null,
    val validationDisplayNameEmpty: Boolean = false
)

class EditProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProfile = true, error = null)
            when (val res = profileRepository.fetchMyProfile()) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        displayName = res.data.displayName,
                        bio = res.data.bio,
                        photoUrl = res.data.photoUrl,
                        isLoadingProfile = false
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoadingProfile = false,
                        error = res.error
                    )
                }
            }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            displayName = value,
            validationDisplayNameEmpty = false,
            savedSuccess = false,
            error = null
        )
    }

    fun onBioChange(value: String) {
        _uiState.value = _uiState.value.copy(
            bio = value,
            savedSuccess = false,
            error = null
        )
    }

    fun saveProfile() {
        val current = _uiState.value
        if (current.displayName.trim().isEmpty()) {
            _uiState.value = current.copy(validationDisplayNameEmpty = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, savedSuccess = false)
            when (val res = profileRepository.updateProfile(current.displayName, current.bio)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        displayName = res.data.displayName,
                        bio = res.data.bio,
                        photoUrl = res.data.photoUrl,
                        isSaving = false,
                        savedSuccess = true
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = res.error)
                }
            }
        }
    }

    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploadingPhoto = true,
                error = null,
                photoUploadedSuccess = false
            )
            when (val res = profileRepository.uploadProfilePhoto(uri)) {
                is ApiResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        photoUrl = res.data,
                        isUploadingPhoto = false,
                        photoUploadedSuccess = true
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isUploadingPhoto = false,
                        error = res.error
                    )
                }
            }
        }
    }
}
