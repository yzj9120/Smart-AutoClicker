package com.buzbuz.smartautoclicker.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


/**
 *
 * private val viewModel: MyViewModel by viewModels {
 *         MyViewModelFactory(NetworkRequestHelper())
 *     }
 *
 *
 *
 *lifecycleScope.launch {
 *             viewModel.responseState.collect { result ->
 *                 when (result) {
 *                     is Result.Loading -> {
 *                         // Show loading state
 *                     }
 *                     is Result.Success -> {
 *                         // Update UI with the fetched data
 *                     }
 *                     is Result.Error -> {
 *                         // Show error message
 *                     }
 *                 }
 *             }
 *         }
 *
 *         // Trigger GET data fetch
 *         viewModel.fetchData("https://api.example.com/data")
 *
 *         // Trigger POST data fetch
 *         val jsonBody = """{"key":"value"}"""
 *         viewModel.postData("https://api.example.com/post", jsonBody)
 *
 *
 */

class MyViewModelFactory(
    private val networkRequestHelper: NetworkRequestHelper
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(FirstViewModel::class.java) -> {
                FirstViewModel(networkRequestHelper) as T
            }
            modelClass.isAssignableFrom(SecondViewModel::class.java) -> {
                SecondViewModel(networkRequestHelper) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

