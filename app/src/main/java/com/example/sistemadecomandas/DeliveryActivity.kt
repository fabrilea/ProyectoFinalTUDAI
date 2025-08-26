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
        generarPDFDelivery(nombre = nombre, telefono = numero, pedidos = pedidos)

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

    private fun generarPDFDelivery(nombre: String, telefono: String, pedidos: List<Pedido>) {
        fun mmToPt(mm: Float): Int = ((mm / 25.4f) * 72f).toInt()

        data class Row(val cant: Int, val desc: String)
        val items = pedidos.map { Row(it.cantidad, it.nombre) }
            .ifEmpty { listOf(Row(1, "(sin detalle)")) }

        // --- Medidas térmica 80mm (72mm útiles) ---
        val PAGE_WIDTH_PT = mmToPt(72f)       // ≈ 204 pt
        val MARGIN_PT = mmToPt(2f)            // ≈ 5 pt
        val CONTENT_W_PT = PAGE_WIDTH_PT - MARGIN_PT * 2

        // Columnas: Cant (18%) | Desc (82%)
        val cantColW = (CONTENT_W_PT * 0.18f).toInt()
        val descColW = CONTENT_W_PT - cantColW

        // --- Tipografías ---
        val titleTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val bodyTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        val boldTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val smallTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        val rulePaint = android.graphics.Paint().apply { strokeWidth = 1f; isAntiAlias = true }

        fun layout(t: CharSequence, tp: android.text.TextPaint, w: Int, a: android.text.Layout.Alignment) =
            android.text.StaticLayout.Builder.obtain(t, 0, t.length, tp, w)
                .setAlignment(a)
                .setIncludePad(false)
                .build()

        val horaStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val titleL = layout("DELIVERY - ${nombre.uppercase()}", titleTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        val telL   = layout("Tel: ${telefono.ifBlank { "(no informado)" }}", bodyTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_NORMAL)
        val horaL  = layout("Hora: $horaStr", bodyTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_OPPOSITE)

        val GAP = 4f
        val RULE_SP = 6f
        var totalH = 0f

        // Medición
        totalH += titleL.height + GAP
        totalH += maxOf(telL.height, horaL.height) + GAP

        val headCantL = layout("Cant", boldTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
        val headDescL = layout("Descripción", boldTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
        val headH = maxOf(headCantL.height, headDescL.height)
        totalH += headH + GAP + 1 + RULE_SP

        items.forEach { r ->
            val cL = layout(r.cant.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val dL = layout(r.desc, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            totalH += maxOf(cL.height, dL.height) + GAP
        }
        totalH += 1 + RULE_SP

        val footL = layout("Gracias", smallTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        totalH += footL.height

        val PAGE_HEIGHT_PT = (totalH + MARGIN_PT * 2).toInt().coerceAtLeast(mmToPt(90f))

        // --- Crear PDF ---
        val fileName = "delivery_${nombre.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(getExternalFilesDir(null), fileName)

        val document = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        var y = MARGIN_PT.toFloat()
        fun rule() {
            canvas.drawLine(MARGIN_PT.toFloat(), y, (PAGE_WIDTH_PT - MARGIN_PT).toFloat(), y, rulePaint)
            y += RULE_SP
        }

        // Encabezado
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); titleL.draw(canvas); canvas.restore()
        y += titleL.height + GAP
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); telL.draw(canvas); canvas.restore()
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); horaL.draw(canvas); canvas.restore()
        y += maxOf(telL.height, horaL.height) + GAP

        // Tabla
        var x = MARGIN_PT.toFloat()
        canvas.save(); canvas.translate(x, y); headCantL.draw(canvas); canvas.restore()
        x += cantColW
        canvas.save(); canvas.translate(x, y); headDescL.draw(canvas); canvas.restore()
        y += headH + GAP; rule()

        items.forEach { r ->
            x = MARGIN_PT.toFloat()
            val cL = layout(r.cant.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val dL = layout(r.desc, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            val rowH = maxOf(cL.height, dL.height)

            canvas.save(); canvas.translate(x, y); cL.draw(canvas); canvas.restore()
            x += cantColW
            canvas.save(); canvas.translate(x, y); dL.draw(canvas); canvas.restore()

            y += rowH + GAP
        }
        rule()

        // Footer
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); footL.draw(canvas); canvas.restore()

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()

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
