package com.example.contactapp.ui.features.keypad.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.contactapp.ui.features.keypad.dialPadKeys

@Composable
fun DialPad(
    onDigitClick: (String) -> Unit,
    showBackground: Boolean = true
) {

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        dialPadKeys.forEach { row ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {

                row.forEach { key ->

                    DialButton(
                        digit = key.digit,
                        letters = key.letters,
                        showBackground = showBackground,

                        onClick = {

                            if (key.digit == "0") {
                                onDigitClick("0")
                            } else {
                                onDigitClick(key.digit)
                            }

                        },
                        onLongClick = {

                            if (key.digit == "0") {
                                onDigitClick("+")
                            }

                        }
                    )

                }

            }

        }

    }

}