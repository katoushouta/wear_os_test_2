package com.example.wear_os_test_2

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

private const val FROM_WEAR_PATH = "/heart_rate_bpm"
private const val VITAL_KEY = "com.example.wear_test_2.vital"
class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MobileApp() }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
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
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }
}

@Composable
fun MobileApp() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Hello Wear", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}