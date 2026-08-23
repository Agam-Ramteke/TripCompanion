package com.tripcompanion.app.feature.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoEditorState(
    val photoId: Long? = null,
    val eventId: Long = 0,
    val title: String = "",
    val referenceImageUri: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false
) {
    /**
     * The image is the photo plan (§15). A title is optional — a photo with no
     * text at all is a valid thing to save — so only the image gates saving.
     */
    val canSave: Boolean get() = referenceImageUri.isNotBlank() && !isSaving
}

@HiltViewModel
class PlannedPhotoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val photoRepository: PlannedPhotoRepository
) : ViewModel() {

    private val eventId: Long = savedStateHandle.get<String>("eventId")?.toLongOrNull() ?: 0L
    private val photoId: Long? = savedStateHandle.get<String>("photoId")?.toLongOrNull()

    private val _state = MutableStateFlow(PhotoEditorState(eventId = eventId))
    val state: StateFlow<PhotoEditorState> = _state.asStateFlow()

    init {
        if (photoId != null) {
            viewModelScope.launch {
                photoRepository.getPhotoById(photoId).firstOrNull()?.let { photo ->
                    _state.update {
                        it.copy(
                            photoId = photo.id,
                            eventId = photo.eventId,
                            title = photo.title,
                            referenceImageUri = photo.referenceImageUri.orEmpty(),
                            isEditing = true
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title) }
    }

    fun updateReferenceUri(uri: String) {
        _state.update { it.copy(referenceImageUri = uri) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val order = if (s.isEditing && s.photoId != null) {
                photoRepository.getPhotoById(s.photoId).firstOrNull()?.order ?: 0
            } else {
                photoRepository.getNextOrder(s.eventId)
            }

            val photo = PlannedPhoto(
                id = s.photoId ?: 0,
                eventId = s.eventId,
                title = s.title.trim(),
                referenceImageUri = s.referenceImageUri.trim().ifBlank { null },
                order = order
            )

            if (s.isEditing) {
                photoRepository.updatePhoto(photo)
            } else {
                photoRepository.insertPhoto(photo)
            }
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
