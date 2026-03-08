package io.github.soclear.oneuix.ui

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.soclear.oneuix.R
import io.github.soclear.oneuix.data.Preference
import io.github.soclear.oneuix.ui.category.Category
import io.github.soclear.oneuix.ui.category.CategoryAppInfo

class SettingViewModel(application: Application) : ViewModel() {
    val categoryAppInfoList: StateFlow<List<CategoryAppInfo>> = flow {
        val packageManager = application.packageManager
        val categoryAppInfoList = Category.entries.mapNotNull { category ->
            val applicationInfo = try {
                packageManager.getApplicationInfo(category.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return@mapNotNull null
            }
            val label = if (category == Category.Other) {
                application.getString(R.string.other)
            } else {
                applicationInfo.loadLabel(packageManager).toString()
            }
            val icon = applicationInfo.loadIcon(packageManager).toBitmap().asImageBitmap()
            CategoryAppInfo(category, label, icon)
        }
        emit(categoryAppInfoList)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dataStore: DataStore<Preference> = application.dataStore

    val preference = dataStore.data.stateIn(
        scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = Preference()
    )

    fun updateData(nextPreference: (currentPreference: Preference) -> Preference) {
        viewModelScope.launch {
            dataStore.updateData {
                nextPreference(it)
            }
        }
    }
}
