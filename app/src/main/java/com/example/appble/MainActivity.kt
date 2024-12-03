package com.example.appble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appble.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var binding: ActivityMainBinding

    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 5000 // 10 segundos

    private var scanning = false
    private val deviceListAdapter = LeDeviceListAdapter { device -> connectToDevice(device) }
    private val bluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private var bluetoothGatt: BluetoothGatt? = null
    private var writableCharacteristic: BluetoothGattCharacteristic? = null

    // UUIDs para o serviço e a característica
    private val SERVICE_UUID: UUID = UUID.fromString("ab0828b1-198e-4351-b779-901fa0e0371e")
    private val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

    private val CHARACTERISTIC_UUID_RX: UUID = UUID.fromString("4ac8a682-9736-4e5d-932b-e9b31405049c")
    private val CHARACTERISTIC_UUID_TX: UUID = UUID.fromString("84d4f420-e7f0-4b0c-b16a-a125b0521aed")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setupRecyclerView()

        binding.buttonScan.setOnClickListener {
            if (hasPermissions()) {
                scanLeDevice()
            } else {
                requestPermissions()
            }
        }

        binding.buttonSend.setOnClickListener {
            val message = binding.editTextMessage.text.toString()
            if (message.isNotEmpty()) {
                sendMessage(message)
            } else {
                Toast.makeText(this, "Digite uma mensagem antes de enviar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceListAdapter
        }
    }

    private fun scanLeDevice() {
        if (!scanning) {
            binding.progressBar.visibility = View.VISIBLE // Mostrar o ProgressBar
            handler.postDelayed({
                stopScanning()
            }, SCAN_PERIOD)
            scanning = true
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            bluetoothLeScanner.startScan(leScanCallback)
            Toast.makeText(this, "Escaneamento iniciado", Toast.LENGTH_SHORT).show()
        } else {
            stopScanning()
        }
    }

    private fun stopScanning() {
        binding.progressBar.visibility = View.GONE // Ocultar o ProgressBar
        scanning = false
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothLeScanner.stopScan(leScanCallback)
        Toast.makeText(this, "Escaneamento finalizado", Toast.LENGTH_SHORT).show()
    }

    // Callback para os resultados do escaneamento
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (device.name != null) {
                deviceListAdapter.addDevice(device)
                deviceListAdapter.notifyDataSetChanged()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE Scan", "Erro no escaneamento: $errorCode")
            Toast.makeText(this@MainActivity, "Erro no escaneamento BLE: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE Connection", "Conectado ao dispositivo: ${device.address}")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Conectado ao dispositivo ${device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE Connection", "Desconectado do dispositivo: ${device.address}")
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Desconectado do dispositivo ${device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    bluetoothGatt = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE Services", "Serviços descobertos: ${gatt.services}")
                    val service = gatt.getService(SERVICE_UUID)
                    writableCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID_RX)
                    val txCharacteristic = service?.getCharacteristic(CHARACTERISTIC_UUID_TX)

                    if (writableCharacteristic == null) {
                        Log.e("BLE Characteristic", "Característica RX não encontrada.")
                    }

                    if (txCharacteristic != null) {
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }

                        // Habilitar notificações na característica TX
                        gatt.setCharacteristicNotification(txCharacteristic, true)
                        val descriptor = txCharacteristic.getDescriptor(
                            UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)
                        )
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        Log.d("BLE Notification", "Notificações habilitadas para a característica TX")
                    } else {
                        Log.e("BLE Characteristic", "Característica TX não encontrada.")
                    }
                } else {
                    Log.w("BLE Services", "Falha ao descobrir serviços: $status")
                }
            }



            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == CHARACTERISTIC_UUID_TX) {
                    val data = characteristic.value.decodeToString() // Decodifica os dados recebidos
                    Log.d("BLE Notification", "Dados recebidos: $data")
                    runOnUiThread {
                        binding.receivedMessageTextView.text = data // Atualiza o TextView com os dados recebidos
                    }
                }
            }


        })
    }


    private fun sendMessage(message: String) {
        writableCharacteristic?.let { characteristic ->
            characteristic.value = message.toByteArray()
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            bluetoothGatt?.writeCharacteristic(characteristic)?.let { success ->
                if (success) {
                    Toast.makeText(this, "Mensagem enviada: $message", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Falha ao enviar mensagem.", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Característica de escrita não encontrada.", Toast.LENGTH_SHORT).show()
        }
    }

    // Verificar permissões necessárias
    private fun hasPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Solicitar permissões
    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}

// Adapter personalizado para exibir dispositivos BLE encontrados
class LeDeviceListAdapter(
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<LeDeviceListAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()

    fun addDevice(device: BluetoothDevice) {
        if (!devices.contains(device)) {
            devices.add(device)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return DeviceViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(
        view: View,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = itemView.findViewById(android.R.id.text1)
        private val addressTextView: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(device: BluetoothDevice) {
            val context = itemView.context // Obter o contexto do itemView
            if (ActivityCompat.checkSelfPermission(
                    context, // Contexto correto aqui
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("BLE Adapter", "Permissão BLUETOOTH_CONNECT ausente.")
                return
            }
            nameTextView.text = device.name ?: "Desconhecido"
            addressTextView.text = device.address
            itemView.setOnClickListener { onClick(device) }
        }
    }
}
