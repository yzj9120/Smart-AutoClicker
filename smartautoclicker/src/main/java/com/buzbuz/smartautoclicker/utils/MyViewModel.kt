package com.buzbuz.smartautoclicker.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FirstViewModel(private val networkRequestHelper: NetworkRequestHelper) : ViewModel() {
    private val _responseState = MutableStateFlow<Result<String>>(Result.Loading)
    val responseState: StateFlow<Result<String>> get() = _responseState

    fun fetchData(url: String) {

        viewModelScope.launch {
            _responseState.value = Result.Loading
            val result = networkRequestHelper.get(url)
            _responseState.value = result
        }
    }
}

class SecondViewModel(private val networkRequestHelper: NetworkRequestHelper) : ViewModel() {
    private val _responseState = MutableStateFlow<Result<String>>(Result.Loading)
    val responseState: StateFlow<Result<String>> get() = _responseState

    fun postData(url: String, jsonBody: String) {
        viewModelScope.launch {
            _responseState.value = Result.Loading
            val result = networkRequestHelper.post(url, jsonBody)
            _responseState.value = result
        }
    }
}
