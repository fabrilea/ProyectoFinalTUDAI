package com.example.sistemadecomandas

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.sistemadecomandas.model.Pedido
import com.example.sistemadecomandas.printer.PdfPrintAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DeliveryActivity : AppCompatActivity() {

    private lateinit var inputNombre: EditText
    private lateinit var inputNumero: EditText
    private lateinit var contenedorResumen: LinearLayout
    private lateinit var btnAgregarProductos: Button
    private lateinit var btnConfirmar: Button
    private lateinit var btnHistorial: Button
    private lateinit var resumenTexto: TextView
    private var pedidos = mutableListOf<Pedido>()
    private lateinit var resultadoPedido: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delivery)

        inputNombre = findViewById(R.id.inputNombreCliente)
        inputNumero = findViewById(R.id.inputNumeroCliente)
        contenedorResumen = findViewById(R.id.listaResumenDelivery)
        resumenTexto = findViewById(R.id.txtResumenDelivery)
        btnAgregarProductos = findViewById(R.id.btnAgregarProductosDelivery)
        btnConfirmar = findViewById(R.id.btnConfirmarDelivery)
        btnHistorial = findViewById(R.id.btnVerDeliveriesAnteriores)

        resultadoPedido = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val json = result.data?.getStringExtra("delivery_json") // ← CAMBIO AQUÍ
                if (!json.isNullOrEmpty()) {
                    val nuevos = com.google.gson.Gson().fromJson(json, Array<Pedido>::class.java).toList()
                    for (p in nuevos) {
                        val existente = pedidos.find { it.nombre == p.nombre }
                        if (existente != null) {
                            existente.cantidad += p.cantidad
                        } else {
                            pedidos.add(p)
                        }
                    }
                    actualizarResumen()
                }
            }
        }


        btnAgregarProductos.setOnClickListener {
            val intent = Intent(this, PedidoActivity::class.java)
            intent.putExtra("es_delivery", true)
            intent.putExtra("mesa", "Delivery") // o algún valor simbólico
            resultadoPedido.launch(intent)
        }

        btnConfirmar.setOnClickListener { confirmarDelivery() }
        btnHistorial.visibility = if (hayDeliveries()) View.VISIBLE else View.GONE
        btnHistorial.setOnClickListener { mostrarHistorialDeliveries() }

        actualizarResumen()
    }

    private fun actualizarResumen() {
        contenedorResumen.removeAllViews()
        var total = 0
        pedidos.forEach {
            val linea = TextView(this)
            val subtotal = it.precio * it.cantidad
            linea.text = "${it.nombre} x${it.cantidad} = $${subtotal}"
            contenedorResumen.addView(linea)
            total += subtotal
        }
        resumenTexto.text = "Total: $${total}"
    }

    private fun confirmarDelivery() {
        val nombre = inputNombre.text.toString()
        val numero = inputNumero.text.toString()

        if (nombre.isBlank() || numero.isBlank() || pedidos.isEmpty()) {
            Toast.makeText(this, "Falta nombre, número o productos", Toast.LENGTH_SHORT).show()
            return
        }

        val resumen = StringBuilder("DELIVERY - $nombre\nTel: $numero\n\n")
        var total = 0
        for (p in pedidos) {
            val subtotal = p.precio * p.cantidad
            resumen.append("${p.nombre} x${p.cantidad} = $${subtotal}\n")
            total += subtotal
        }
        resumen.append("\nTOTAL: $${total}")

        guardarDeliveryEnResumen(total)
        generarPDFDelivery(resumen.toString(), nombre)

        getSharedPreferences("estado_deliveries", MODE_PRIVATE)
            .edit().putBoolean("hay_deliveries", true).apply()

        Toast.makeText(this, "Delivery registrado", Toast.LENGTH_SHORT).show()
    }


    private fun guardarDeliveryEnResumen(total: Int) {
        val prefs = getSharedPreferences("resumen_dia", MODE_PRIVATE)
        val anterior = prefs.getString("delivery", "") ?: ""
        val actualizado = anterior + "|$total"
        prefs.edit().putString("delivery", actualizado).apply()
    }

    private fun generarPDFDelivery(resumen: String, nombre: String) {
        val hora = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date())
        val fileName = "delivery_${nombre.replace(" ", "_")}_$hora.pdf"
        val file = File(getExternalFilesDir(null), fileName)

        val document = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 12f }

        val lines = resumen.split("\n")
        var y = 20
        for (line in lines) {
            canvas.drawText(line, 10f, y.toFloat(), paint)
            y += 20
        }

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()

        // 🔹 Guardar en delivery_historial
        val total = resumen.lines().lastOrNull()?.removePrefix("TOTAL: $")?.toIntOrNull() ?: 0
        val deliveryString = "$fileName-$$total"

        val prefs = getSharedPreferences("delivery_historial", MODE_PRIVATE)
        val existingSet = prefs.getStringSet("historial", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        existingSet.add(deliveryString)
        prefs.edit().putStringSet("historial", existingSet).apply()

        abrirPDF(file)
    }

    private fun abrirPDF(file : File){
        if (!file.exists()) {
            Toast.makeText(this, "El archivo no existe", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)

        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        val printAdapter: PrintDocumentAdapter = PdfPrintAdapter(this, uri)

        val printJob = printManager.print("Comanda o Factura", printAdapter, PrintAttributes.Builder().build())

        // Monitorear estado de impresión para cerrar la actividad SOLO cuando se complete o falle
        Thread {
            while (!printJob.isCompleted && !printJob.isCancelled && !printJob.isFailed) {
                Thread.sleep(500)
            }

            runOnUiThread {
                finish() // Se cierra después de que el usuario imprima o salga del diálogo
            }
        }.start()
    }


    private fun hayDeliveries(): Boolean {
        return getSharedPreferences("estado_deliveries", MODE_PRIVATE)
            .getBoolean("hay_deliveries", false)
    }

    private fun mostrarHistorialDeliveries() {
        val archivos = getExternalFilesDir(null)?.listFiles()
        val deliveries = archivos?.filter { it.name.startsWith("delivery_") && it.name.endsWith(".pdf") } ?: emptyList()

        val nombres = deliveries.map { it.name }
        AlertDialog.Builder(this)
            .setTitle("Deliveries anteriores")
            .setItems(nombres.toTypedArray()) { _, index ->
                val file = deliveries[index]
                abrirPDF(file)

            }
            .show()
    }
}
