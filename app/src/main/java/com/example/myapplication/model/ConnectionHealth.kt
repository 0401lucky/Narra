package com.example.myapplication.model

/** 提供商连接健康状态。 */
enum class ConnectionHealth {
    /** 尚未检测。 */
    UNKNOWN,

    /** 正在检测中。 */
    CHECKING,

    /** 连接正常。 */
    HEALTHY,

    /** 连接异常。 */
    UNHEALTHY,
}
