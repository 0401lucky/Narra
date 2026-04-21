package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 收敛各 ViewModel.Factory 里重复的 `@Suppress("UNCHECKED_CAST") ... as T`：
 *
 * - 泛型参数 `VM` 作为实际构造产物，外层 `create<T>(modelClass)` 的 `T` 必须为 `VM` 的超类；
 * - 调用方若传入不兼容类型，立即抛 IllegalArgumentException，避免延迟到强转点才崩。
 *
 * 用法：
 * ```
 * fun factory(dep: Dep): ViewModelProvider.Factory =
 *     typedViewModelFactory { MyViewModel(dep) }
 * ```
 */
inline fun <reified VM : ViewModel> typedViewModelFactory(
    crossinline creator: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(VM::class.java)) {
            "Unexpected ViewModel class: ${modelClass.name}, expected ${VM::class.java.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}
