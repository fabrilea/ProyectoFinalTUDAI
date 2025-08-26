package com.example.sistemadecomandas

import android.app.Activity
import android.app.AlertDialog
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.text.TextPaint
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.sistemadecomandas.model.Pedido
import com.example.sistemadecomandas.model.Producto
import com.example.sistemadecomandas.printer.PdfPrintAdapter
import com.example.sistemadecomandas.repository.ProductoRepository
import com.google.gson.Gson
import java.io.File


class PedidoActivity : AppCompatActivity() {

    private val pedidos = mutableListOf<Pedido>()
    private lateinit var nombreMesa: String
    private lateinit var totalView: TextView
    private lateinit var lista: ListView
    private lateinit var contenedorSeleccionados: LinearLayout
    private lateinit var btnConfirmar: Button
    private var esDelivery = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pedido)

        nombreMesa = intent.getStringExtra("mesa") ?: "Mesa desconocida"
        esDelivery = intent.getBooleanExtra("es_delivery", false)

        val spinner = findViewById<Spinner>(R.id.spinnerCategoria)
        lista = findViewById(R.id.listaProductos)
        totalView = findViewById(R.id.totalPedido)
        contenedorSeleccionados = findViewById(R.id.contenedorSeleccionados)
        btnConfirmar = findViewById(R.id.btnConfirmarPedido)
        btnConfirmar.isEnabled = false

        val categorias = ProductoRepository.obtenerCategorias()
        spinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                actualizarLista(categorias[pos])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnConfirmar.setOnClickListener {
            val gson = Gson()

            if (esDelivery) {
                val result = Intent().apply {
                    putExtra("delivery_json", gson.toJson(pedidos))
                    putExtra("monto_total", pedidos.sumOf { it.precio * it.cantidad })
                }
                setResult(Activity.RESULT_OK, result)
                finish()
            } else {
                val prefs = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
                val acumuladoJson = prefs.getString(nombreMesa, null)
                val acumulado = if (acumuladoJson != null) {
                    gson.fromJson(acumuladoJson, Array<Pedido>::class.java).toMutableList()
                } else mutableListOf()

                for (nuevo in pedidos) {
                    val existente = acumulado.find { it.nombre == nuevo.nombre }
                    if (existente != null) {
                        existente.cantidad += nuevo.cantidad
                    } else {
                        acumulado.add(nuevo)
                    }
                }

                val resultIntent = Intent().apply {
                    putExtra("ocupada", true)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                prefs.edit().putString(nombreMesa, gson.toJson(acumulado)).apply()


                generarComandaPDF(pedidos)
            }
        }

    }


    // Genera COMANDA 80mm (vertical), altura dinámica, sin precios
    private fun generarComandaPDF(pedidos: List<Pedido>) {
        fun mmToPt(mm: Float): Int = ((mm / 25.4f) * 72f).toInt()

        // --- Datos base ---
        val mesa = nombreMesa
        val ahora = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

        val PAGE_WIDTH_PT = mmToPt(72f)   // ancho real de impresión ≈ 204 pt
        val MARGIN_PT = mmToPt(2f)        // márgenes chicos ≈ 5 pt
        val CONTENT_W_PT = PAGE_WIDTH_PT - MARGIN_PT * 2

// Columnas
        val cantColW = (CONTENT_W_PT * 0.15f).toInt()
        val descColW = CONTENT_W_PT - cantColW

// Tipos de letra
        val titleTP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bodyTP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val boldTP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val smallTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        val rulePaint = android.graphics.Paint().apply { strokeWidth = 1f; isAntiAlias = true }

        fun layout(text: CharSequence, tp: android.text.TextPaint, w: Int, align: android.text.Layout.Alignment) =
            android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, tp, w)
                .setAlignment(align)
                .setIncludePad(false)
                .build()

        // --- Encabezados ---
        val titleL = layout("COMANDA - ${mesa.uppercase()}", titleTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        val dateL  = layout("Hora: $ahora", bodyTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_OPPOSITE)

        // --- Medición de altura total (exacta) ---
        val GAP = 4f
        val RULE_SP = 6f
        var totalH = 0f

        totalH += titleL.height + GAP + 1 + RULE_SP
        totalH += dateL.height + GAP

        // Encabezado de tabla
        val headCantL = layout("Cant", boldTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
        val headDescL = layout("Descripción", boldTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
        val headH = maxOf(headCantL.height, headDescL.height)
        totalH += headH + GAP + 1 + RULE_SP

        // Filas: usamos la lista recibida (sin precios)
        pedidos.forEach { p ->
            val cL = layout(p.cantidad.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val dL = layout(p.nombre, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            totalH += maxOf(cL.height, dL.height) + GAP
        }
        totalH += 1 + RULE_SP

        // Pie
        val footL = layout("Preparar y enviar a mesa", smallTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        totalH += footL.height

        val PAGE_HEIGHT_PT = (totalH + MARGIN_PT * 2).toInt().coerceAtLeast(mmToPt(90f))

        // --- Crear PDF y dibujar ---
        val fileName = "comanda_${mesa.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
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

        // Título
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); titleL.draw(canvas); canvas.restore()
        y += titleL.height + GAP; rule()

        // Hora
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); dateL.draw(canvas); canvas.restore()
        y += dateL.height + GAP

        // Encabezado de tabla
        var x = MARGIN_PT.toFloat()
        canvas.save(); canvas.translate(x, y); headCantL.draw(canvas); canvas.restore()
        x += cantColW
        canvas.save(); canvas.translate(x, y); headDescL.draw(canvas); canvas.restore()
        y += headH + GAP; rule()

        // Filas
        pedidos.forEach { p ->
            x = MARGIN_PT.toFloat()
            val cL = layout(p.cantidad.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val dL = layout(p.nombre, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            val rowH = maxOf(cL.height, dL.height)

            canvas.save(); canvas.translate(x, y); cL.draw(canvas); canvas.restore()
            x += cantColW
            canvas.save(); canvas.translate(x, y); dL.draw(canvas); canvas.restore()
            y += rowH + GAP
        }
        rule()

        // Pie
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); footL.draw(canvas); canvas.restore()

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()

        // Guardá el nombre para reimpresión (si querés seguir usándolo)
        getSharedPreferences("comandas_pdf", MODE_PRIVATE)
            .edit()
            .putString(mesa, file.name)
            .apply()

        abrirPDF(file)
    }



    private fun actualizarLista(categoria: String) {
        val productos = ProductoRepository.obtenerPorCategoria(categoria)
        val nombres = productos.map { "${it.nombre} - $${it.precio}" }
        lista.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nombres)

        lista.setOnItemClickListener { _, _, position, _ ->
            val producto = productos[position]
            mostrarDialogoAgregar(producto)
        }
    }

    private fun mostrarDialogoAgregar(producto: Producto) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 20
            value = 1
        }

        layout.addView(picker)

        AlertDialog.Builder(this)
            .setTitle("Cantidad para ${producto.nombre}")
            .setView(layout)
            .setPositiveButton("Agregar") { _, _ ->
                val existente = pedidos.find { it.nombre == producto.nombre }
                if (existente != null) {
                    existente.cantidad += picker.value
                } else {
                    pedidos.add(
                        Pedido(
                            producto.nombre,
                            producto.precio,
                            picker.value,
                            producto.categoria
                        )
                    )
                }
                actualizarTotal()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarTotal() {
        contenedorSeleccionados.removeAllViews()
        var total = 0.0

        pedidos.sortedWith(compareBy({ it.categoria }, { it.nombre }))
            .forEachIndexed { index, pedido ->
                val fila = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(8, 8, 8, 8)
                }

                val txt = TextView(this).apply {
                    text =
                        "[${pedido.categoria}] ${pedido.nombre} x${pedido.cantidad} = $${pedido.precio * pedido.cantidad}"
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val btnEditar = Button(this).apply {
                    text = "✏️"
                    setOnClickListener { mostrarDialogoEditar(pedido, index) }
                }

                val btnEliminar = Button(this).apply {
                    text = "🗑️"
                    setOnClickListener {
                        pedidos.removeAt(index)
                        actualizarTotal()
                    }
                }

                fila.addView(txt)
                fila.addView(btnEditar)
                fila.addView(btnEliminar)
                contenedorSeleccionados.addView(fila)

                total += pedido.precio * pedido.cantidad
            }

        totalView.text = "Total: $${total}"
        btnConfirmar.isEnabled = pedidos.isNotEmpty()
    }

    private fun mostrarDialogoEditar(pedido: Pedido, index: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val picker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 20
            value = pedido.cantidad
        }

        layout.addView(picker)

        AlertDialog.Builder(this)
            .setTitle("Editar cantidad para ${pedido.nombre}")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                pedidos[index] = pedido.copy(cantidad = picker.value)
                actualizarTotal()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }


    private fun abrirPDF(file: File) {
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


}


