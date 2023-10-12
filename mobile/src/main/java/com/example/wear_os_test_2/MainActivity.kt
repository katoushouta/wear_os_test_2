package com.example.wear_os_test_2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

private const val FROM_WEAR_PATH = "/heart_rate_bpm"
private const val VITAL_KEY = "com.example.wear_test_2.vital"
class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {
    private val vitalData = mutableStateOf("--")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileApp(vitalData) }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("DEBUG", "on data change")
        dataEvents.forEach { it ->
            val uri = it.dataItem.uri
            val nodeId = uri.host
            val payload = uri.toString().toByteArray()
            Log.d("DEBUG", "uri : $uri")
            Log.d("DEBUG", "nodeID : $nodeId")
            Log.d("DEBUG", "payload : $payload")
            it.dataItem.also { item ->
                if ((item.uri.path?.compareTo(FROM_WEAR_PATH) ?: -1) == 0) {
                    DataMapItem.fromDataItem(item).dataMap.apply {
                        Toast.makeText(applicationContext, getInt(VITAL_KEY).toString(), Toast.LENGTH_SHORT).show()
                        vitalData.value = getInt(VITAL_KEY).toString()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        Log.d("DEBUG", "onPause")
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }
}

@Composable
fun MobileApp(vitalData: MutableState<String>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hello Wear", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("心拍数")
            Spacer(modifier = Modifier.width(16.dp))
            Text(vitalData.value)
        }
    }
}