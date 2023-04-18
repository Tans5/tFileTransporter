package com.tans.tfiletransporter.transferproto

interface SimpleCallback<T> {

    fun onError(errorMsg: String) {}

    fun onSuccess(data: T) {}
}