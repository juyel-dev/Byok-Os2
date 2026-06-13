package com.example.feature.chat.viewmodel

sealed class NavigationEvent {
    object NavigateToProviders : NavigationEvent()
}
