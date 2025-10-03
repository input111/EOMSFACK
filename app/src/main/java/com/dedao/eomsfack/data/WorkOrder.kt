package com.dedao.eomsfack.data

data class WorkOrder(
    val id: String,
    val workOrderId: String,
    val oldPonPort: String,
    val newPonPort: String,
    val generatedText: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isEmailSent: Boolean = false
)