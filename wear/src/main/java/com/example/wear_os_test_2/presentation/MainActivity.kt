/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

// 参考
// 心拍数取得 -> https://developer.android.com/training/wearables/health-services/active?hl=ja
// データ送信 -> https://developer.android.com/training/wearables/data/data-items?hl=ja

package com.example.wear_os_test_2.presentation

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.unregisterMeasureCallback
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.wear_os_test_2.R
import com.example.wear_os_test_2.presentation.theme.Wear_os_test_2Theme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.request.DataSourcesRequest
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

private const val TO_PHONE_PATH = "/heart_rate_bpm"
private const val VITAL_KEY = "com.example.wear_test_2.vital"
class MainActivity : ComponentActivity() {
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    lateinit var healthClient: HealthServicesClient
    lateinit var measureClient: MeasureClient

    lateinit var dataClient: DataClient

    private fun sendHeartRate(bpm: Double) {
        val putDataReq = PutDataMapRequest.create(TO_PHONE_PATH).run {
            dataMap.putInt(VITAL_KEY, bpm.toInt())
            asPutDataRequest()
        }
        val putDataTask = dataClient.putDataItem(putDataReq)

        putDataTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(applicationContext, "同期しました : $bpm", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "同期に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @ExperimentalCoroutinesApi
    fun heartRateMeasureFlow() = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                // Only send back DataTypeAvailability (not LocationAvailability)
                if (availability is DataTypeAvailability) {
                    Log.d("DEBUG", "availability: $availability")
                    trySendBlocking(availability)
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateBpm = data.getData(DataType.HEART_RATE_BPM)
                val bpm = heartRateBpm.last().value
                val current = LocalDateTime.now()
                if (current.second % 5 == 0) {
                    Log.d("DEBUG", "BPM: $bpm")
                    trySendBlocking(bpm)
                }
            }
        }

        Log.d("DEBUG", "Registering for data")
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)

        awaitClose {
            Log.d("DEBUG", "Unregistering for data")
            runBlocking {
                measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, callback)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("FULL", "start")
        healthClient = HealthServices.getClient(this /*context*/)
        measureClient  = healthClient.measureClient

        dataClient = Wearable.getDataClient(applicationContext)

        permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
            Log.d("FULL", "when")
            when (result) {
                true -> {
                    Log.i("DEBUG", "Body sensors permission granted")
                    // Only measure while the activity is at least in STARTED state.
                    // MeasureClient provides frequent updates, which requires increasing the
                    // sampling rate of device sensors, so we must be careful not to remain
                    // registered any longer than necessary.
                    lifecycleScope.launchWhenStarted {
                        heartRateMeasureFlow().collect {
                            when (it) {
                                is Availability -> {
                                    Log.d("DEBUG", "Availability changed: $it")
                                }
                                is Double -> {
                                    sendHeartRate(it)
                                    Log.d("DEBUG", "Data update: $it")
                                }
                            }
                        }
                    }
                }
                false -> Log.i("DEBUG", "Body sensors permission not granted")
            }
        }
        var supportsHeartRate = false
        lifecycleScope.launch {
            val capabilities = measureClient.getCapabilities()
            supportsHeartRate = DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
            Log.d("DEBUG", supportsHeartRate.toString())
        }

        setContent {
            WearApp(supportsHeartRate.toString())
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStart() {
        super.onStart()
        permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)

        val fitnessOptions = FitnessOptions.builder()
            .addDataType(com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA).build()
        if (!GoogleSignIn.hasPermissions(
                GoogleSignIn.getLastSignedInAccount(this),
                fitnessOptions
            )
        ) {
            Log.d("DEBUG", "permissions false")
            GoogleSignIn.requestPermissions(
                this,
                1,
                GoogleSignIn.getLastSignedInAccount(this),
                fitnessOptions
            )
        }
        GoogleSignIn.getLastSignedInAccount(this)?.let {
            Log.d("DEBUG", "LET")
            Fitness.getSensorsClient(this, it)
                .findDataSources(
                    DataSourcesRequest.Builder()
                        .setDataTypes(com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA)
                        .setDataSourceTypes(DataSource.TYPE_RAW)
                        .build())
                .addOnSuccessListener { dataSources ->
                    dataSources.forEach {
                        Log.i("DEBUG", "Data source found: ${it.streamIdentifier}")
                        Log.i("DEBUG", "Data Source type: ${it.dataType.name}")

                        if (it.dataType == com.google.android.gms.fitness.data.DataType.TYPE_STEP_COUNT_DELTA) {
                            Log.i("DEBUG", "Data source for STEP_COUNT_DELTA found!")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("DEBUG", "Find data sources request failed", e)
                }
        }
    }
}

@Composable
fun WearApp(greetingName: String) {
    Wear_os_test_2Theme {
        /* If you have enough items in your list, use [ScalingLazyColumn] which is an optimized
         * version of LazyColumn for wear devices with some added features. For more information,
         * see d.android.com/wear/compose.
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            verticalArrangement = Arrangement.Center
        ) {
            Greeting(greetingName = "android")
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}