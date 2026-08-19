package com.example.contactapp.ui.features.keypad

import com.example.contactapp.domain.model.DialKey

val dialPadKeys = listOf(

    listOf(
        DialKey("1"),
        DialKey("2","ABC"),
        DialKey("3","DEF")
    ),

    listOf(
        DialKey("4","GHI"),
        DialKey("5","JKL"),
        DialKey("6","MNO")
    ),

    listOf(
        DialKey("7","PQRS"),
        DialKey("8","TUV"),
        DialKey("9","WXYZ")
    ),

    listOf(
        DialKey("*"),
        DialKey("0","+"),
        DialKey("#")
    )
)