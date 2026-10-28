// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TorchHardware { AVAILABLE, NO_FLASH, IN_USE }

/**
 * Camera flash as a torch through [CameraManager.setTorchMode]. This API does not need the
 * CAMERA permission. It fails when another app is using the camera; callers get `false`.
 */
class TorchController(context: Context) {

    private val cm: CameraManager? = ContextCompat.getSystemService(context, CameraManager::class.java)
    private val hasFlashFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private val cameraId: String? = runCatching {
        cm?.cameraIdList?.firstOrNull { id ->
            val c = cm.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm?.cameraIdList?.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private val _on = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _on.asStateFlow()

    private val _hardware = MutableStateFlow(if (hasFlashFeature && cameraId != null) TorchHardware.AVAILABLE else TorchHardware.NO_FLASH)
    val hardware: StateFlow<TorchHardware> = _hardware.asStateFlow()

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) { _on.value = enabled; _hardware.value = TorchHardware.AVAILABLE }
        }
        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) { _on.value = false; _hardware.value = TorchHardware.IN_USE }
        }
    }

    init {
        if (cameraId != null) runCatching { cm?.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    }

    val available: Boolean get() = cameraId != null && hasFlashFeature

    /** Returns true when the request was accepted by the camera service. */
    fun set(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return try {
            cm?.setTorchMode(id, on)
            _on.value = on
            true
        } catch (e: CameraAccessException) {
            _hardware.value = TorchHardware.IN_USE
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}
