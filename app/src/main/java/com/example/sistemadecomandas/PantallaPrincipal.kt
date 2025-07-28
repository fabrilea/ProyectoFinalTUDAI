package com.example.sistemadecomandas

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.sistemadecomandas.model.Pedido
import com.example.sistemadecomandas.utils.RegistroPagos
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PantallaPrincipal : AppCompatActivity() {

    private lateinit var resultadoMesa: ActivityResultLauncher<Intent>
    private val estadosMesas = mutableMapOf<String, Boolean>()
    private lateinit var mesaStateManager: MesaStateManager
    private lateinit var gridMesas: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pantalla_principal)

        mesaStateManager = MesaStateManager(this)
        gridMesas = findViewById(R.id.gridMesas)

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val horizontalMarginPx = (32 * displayMetrics.density).toInt()
        gridMesas.layoutParams.width = screenWidth - horizontalMarginPx

        resultadoMesa = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val nombreMesa = data?.getStringExtra("mesa") ?: return@registerForActivityResult
                val ocupada = data.getBooleanExtra("ocupada", false)

                estadosMesas[nombreMesa] = ocupada
                mesaStateManager.guardarEstado(nombreMesa, ocupada)


                RegistroPagos.cargarDesdePreferencias(this)

                actualizarColoresMesas()
            }
        }


        estadosMesas.putAll(mesaStateManager.obtenerTodosLosEstados())
        RegistroPagos.cargarDesdePreferencias(this)
        construirVistaMesas()
        actualizarColoresMesas()
    }

    override fun onResume() {
        super.onResume()
        RegistroPagos.cargarDesdePreferencias(this)
        actualizarColoresMesas()
    }

    private fun construirVistaMesas() {
        for (i in 1..11) {
            val mesaView = LayoutInflater.from(this).inflate(R.layout.item_mesa, gridMesas, false)
            val texto = mesaView.findViewById<TextView>(R.id.txtMesa)
            texto.setText("Mesa $i")

            val cardView = mesaView.findViewById<CardView>(R.id.cardMesa)
            val nombreMesa = texto.text.toString()

            cardView.setOnClickListener {
                val intent = Intent(this, DetalleMesa::class.java)
                intent.putExtra("mesa", nombreMesa)
                intent.putExtra("ocupada", estadosMesas[nombreMesa] ?: false)
                resultadoMesa.launch(intent)
            }

            gridMesas.addView(mesaView)
        }
    }

    private fun actualizarColoresMesas() {
        val sharedPedidos = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
        val pagos = RegistroPagos.obtenerMapaCompleto()

        for (i in 0 until gridMesas.childCount) {
            val mesaView = gridMesas.getChildAt(i)
            val texto = mesaView.findViewById<TextView>(R.id.txtMesa)
            val cardView = mesaView.findViewById<CardView>(R.id.cardMesa)

            val nombreMesa = texto.text.toString()
            val ocupada = estadosMesas[nombreMesa] ?: false
            var color = if (ocupada) R.color.red else R.color.lightGreen

            if (ocupada) {
                val json = sharedPedidos.getString(nombreMesa, null)
                if (!json.isNullOrEmpty()) {
                    val pedidos: List<Pedido> = Gson().fromJson(json, object : TypeToken<List<Pedido>>() {}.type)
                    val total = pedidos.sumOf { it.precio * it.cantidad }
                    val totalPagado = pagos[nombreMesa]?.values?.sum() ?: 0

                    if (totalPagado in 1 until total) {
                        color = R.color.yellow
                    }
                }
            }

            cardView.setCardBackgroundColor(ContextCompat.getColor(this, color))
        }
    }
}
