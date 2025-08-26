package com.example.sistemadecomandas

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.sistemadecomandas.model.Pedido
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.text.TextPaint
import com.example.sistemadecomandas.printer.PdfPrintAdapter
import com.example.sistemadecomandas.utils.RegistroPagos

class DetalleMesa : AppCompatActivity() {

    private var estaOcupada = false
    private lateinit var resultadoPedido: ActivityResultLauncher<Intent>
    private lateinit var contenedorPedidos: LinearLayout
    private lateinit var txtEstado: TextView
    private lateinit var nombreMesa: String
    private lateinit var btnReimprimir: Button
    private lateinit var btnCerrar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_mesa)

        RegistroPagos.cargarDesdePreferencias(this)

        nombreMesa = intent.getStringExtra("mesa") ?: "Mesa desconocida"
        val prefs = getSharedPreferences("estado_mesas", MODE_PRIVATE)
        estaOcupada = prefs.getBoolean(nombreMesa, false)

        val txtTitulo = findViewById<TextView>(R.id.txtDetalleMesa)
        txtEstado = findViewById(R.id.txtEstadoMesa)
        contenedorPedidos = findViewById(R.id.listaPedidos)
        val btnToggle = findViewById<Button>(R.id.btnAgregarPedido)
        btnReimprimir = findViewById(R.id.btnReimprimirComanda)
        btnCerrar = findViewById(R.id.btnCerrarCuenta)

        txtTitulo.text = "Detalles de $nombreMesa"
        txtEstado.text = if (estaOcupada) "Estado: Ocupada" else "Estado: Libre"

        mostrarPedidos()
        verificarReimpresionComanda()

        resultadoPedido = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val ocupada = result.data?.getBooleanExtra("ocupada", false) ?: false
                estaOcupada = ocupada
                txtEstado.text = if (ocupada) "Estado: Ocupada" else "Estado: Libre"
                getSharedPreferences("estado_mesas", MODE_PRIVATE).edit().putBoolean(nombreMesa, ocupada).apply()

                mostrarPedidos()
                verificarReimpresionComanda()

                // Solo enviar resultado para que MainActivity sepa del cambio
                setResult(RESULT_OK, Intent().apply {
                    putExtra("mesa", nombreMesa)
                    putExtra("ocupada", estaOcupada)
                })
                // Ya no uses finish() acá
            }
        }



        btnToggle.setOnClickListener {
            val intent = Intent(this, PedidoActivity::class.java)
            intent.putExtra("mesa", nombreMesa)
            intent.putExtra("ocupada", estaOcupada)
            resultadoPedido.launch(intent)
        }



        btnCerrar.setOnClickListener {
            mostrarResumenYCerrar()
        }
    }

    private fun verificarReimpresionComanda() {
        btnReimprimir.visibility = if (!estaOcupada) View.GONE else View.VISIBLE
        btnCerrar.visibility = if (!estaOcupada) View.GONE else View.VISIBLE

        if (!estaOcupada) return

        val directorio = getExternalFilesDir(null)
        val archivos = directorio?.listFiles()
        val ultimaComanda = archivos
            ?.filter { it.name.startsWith("comanda_${nombreMesa.replace(" ", "_")}") && it.name.endsWith(".pdf") }
            ?.maxByOrNull { it.lastModified() }

        if (ultimaComanda != null) {
            btnReimprimir.setOnClickListener {
                abrirPDF(ultimaComanda)
            }
        } else {
            btnReimprimir.visibility = View.GONE
        }
    }

    private fun mostrarPedidos() {
        contenedorPedidos.removeAllViews()
        val json = getSharedPreferences("pedidos_mesas", MODE_PRIVATE).getString(nombreMesa, null)
        if (json.isNullOrEmpty()) {
            val sinPedidos = TextView(this).apply {
                text = "No hay productos cargados para esta mesa."
                textSize = 16f
            }
            contenedorPedidos.addView(sinPedidos)
            return
        }

        val pedidos = Gson().fromJson<List<Pedido>>(json, object : TypeToken<List<Pedido>>() {}.type)
        var total = 0
        for (pedido in pedidos) {
            val subtotal = pedido.precio * pedido.cantidad
            contenedorPedidos.addView(TextView(this).apply {
                text = "${pedido.nombre} - ${pedido.cantidad} x $${pedido.precio} = $${subtotal}"
                textSize = 16f
            })
            total += subtotal
        }

        contenedorPedidos.addView(TextView(this).apply {
            text = "Total acumulado: $${total}"
            textSize = 18f
        })
    }

    private fun mostrarResumenYCerrar() {
        val prefs = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
        val json = prefs.getString(nombreMesa, null)

        if (json.isNullOrEmpty()) {
            Toast.makeText(this, "No hay pedidos para cerrar la cuenta.", Toast.LENGTH_SHORT).show()
            return
        }

        val tipoLista = object : TypeToken<List<Pedido>>() {}.type
        val pedidos = Gson().fromJson<List<Pedido>>(json, tipoLista)

        RegistroPagos.cargarDesdePreferencias(this)
        val total = pedidos.sumOf { it.precio * it.cantidad }
        val pagado = RegistroPagos.obtenerTotalPagado(nombreMesa)
        val pendiente = total - pagado

        val opciones = arrayOf("Tarjeta Crédito", "Tarjeta Débito", "Transferencia", "Efectivo")
        val input = EditText(this).apply {
            hint = "Monto a pagar (pendiente: $${pendiente})"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@DetalleMesa, android.R.layout.simple_spinner_dropdown_item, opciones.toList())
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)

            addView(TextView(this@DetalleMesa).apply {
                text = "💳 Elegí método de pago:"
                textSize = 16f
                setPadding(0, 0, 0, 10)
            })

            addView(spinner)

            addView(TextView(this@DetalleMesa).apply {
                text = "\n💵 Ingresá el monto a pagar:"
                textSize = 16f
                setPadding(0, 20, 0, 10)
            })

            addView(input)
        }

        val resumenCompleto = buildString {
            appendLine("💰 Total:     $${total}\n")
            appendLine("✅ Pagado:    $${pagado}\n")
            appendLine("⏳ Pendiente: $${pendiente}\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Resumen del pedido")
            .setMessage(resumenCompleto)
            .setView(layout)
            .setPositiveButton("💾 Registrar pago") { _, _ ->
                val metodo = spinner.selectedItem.toString()
                val montoStr = input.text.toString()
                val monto = montoStr.toIntOrNull() ?: -1

                if (monto <= 0.0 || monto > pendiente) {
                    Toast.makeText(this, "Monto inválido", Toast.LENGTH_SHORT).show()
                } else {
                    RegistroPagos.registrarPago(nombreMesa, metodo, monto)
                    RegistroPagos.guardarEnPreferencias(this)
                    // Recalcular valores actualizados
                    val totalPagadoActual = RegistroPagos.obtenerTotalPagado(nombreMesa)
                    val nuevoPendiente = total - totalPagadoActual

                    if (nuevoPendiente <= 0) {
                        cerrarCuenta()
                    } else {
                        Toast.makeText(
                            this,
                            "Pago parcial registrado. Restante: $${nuevoPendiente}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                }
            }
            .setNeutralButton("🧾 Reimprimir factura") { _, _ ->
                val resumenFactura = construirResumenFactura(pedidos)
                generarFacturaPDF(nombreMesa, resumenFactura)
                Toast.makeText(this, "Factura reimpresa", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }



    private fun cerrarCuenta() {
        val prefs = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
        val json = prefs.getString(nombreMesa, null)

        val tipoLista = object : TypeToken<List<Pedido>>() {}.type
        val pedidos = Gson().fromJson<List<Pedido>>(json, tipoLista)


        guardarPedidosEnResumen(pedidos)

        estaOcupada = false
        getSharedPreferences("estado_mesas", MODE_PRIVATE).edit().putBoolean(nombreMesa, false).apply()
        getSharedPreferences("pedidos_mesas", MODE_PRIVATE).edit().remove(nombreMesa).apply()

        setResult(RESULT_OK, Intent().apply {
            putExtra("mesa", nombreMesa)
            putExtra("ocupada", estaOcupada)
        })

        val hora = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
        val nombreMesaHora = "$nombreMesa ($hora)"
        RegistroPagos.renombrarEntrada(nombreMesa, nombreMesaHora)
        RegistroPagos.eliminarEntrada(nombreMesa)
        RegistroPagos.guardarEnPreferencias(this)

        finish()
    }

    private fun guardarPedidosEnResumen(pedidos: List<Pedido>) {
        val prefs = getSharedPreferences("resumen_dia", MODE_PRIVATE)
        val gson = Gson()
        val listaActualJson = prefs.getString("lista", null)
        val listaActual = if (!listaActualJson.isNullOrEmpty()) {
            gson.fromJson(listaActualJson, object : TypeToken<List<Pedido>>() {}.type)
        } else emptyList<Pedido>()

        val nuevaLista = listaActual + pedidos
        prefs.edit().putString("lista", gson.toJson(nuevaLista)).apply()
    }


    private fun construirResumenFactura(pedidos: List<Pedido>): String {
        val builder = StringBuilder("FACTURA - $nombreMesa\n\n")
        var total = 0
        for (p in pedidos) {
            val subtotal = p.precio * p.cantidad
            builder.append("${p.nombre} x${p.cantidad} = $${subtotal}\n")
            total += subtotal
        }
        builder.append("\nTOTAL: $${total}")
        return builder.toString()
    }

    private fun generarFacturaPDF(nombreMesa: String, resumen: String) {
        fun mmToPt(mm: Float): Int = ((mm / 25.4f) * 72f).toInt()
        fun parseAmount(s: String): Double = s
            .replace("$", "", true)
            .replace("€", "", true)
            .replace("ARS", "", true)
            .replace(".", "")      // 1.234,56 -> 1234,56
            .replace(",", ".")     // 1234,56  -> 1234.56
            .trim()
            .toDoubleOrNull() ?: 0.0

        data class Row(val cant: Int, val desc: String, val rowTotal: Double)

        // --- Parseo de líneas del resumen (acepta varios formatos) ---
        val items = mutableListOf<Row>()
        resumen.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                if (line.startsWith("TOTAL", true) || line.startsWith("FACTURA", true)) return@forEach

                // "desc xCant = $SUBTOTAL"
                Regex("""^\s*(.+?)\s*[xX]\s*(\d+)\s*=\s*([$€]?\s*[-+]?\d+(?:[.,]\d{1,2})?)\s*$""")
                    .find(line)?.let { m ->
                        val d = m.groupValues[1].trim()
                        val c = m.groupValues[2].toIntOrNull() ?: 1
                        val sub = parseAmount(m.groupValues[3])
                        items += Row(c, d, sub)
                        return@forEach
                    }

                // "cant; desc; unit" -> subtotal = cant * unit
                if (';' in line) {
                    val p = line.split(';').map { it.trim() }
                    if (p.size >= 3) {
                        val c = p[0].filter { it.isDigit() }.toIntOrNull() ?: 1
                        val d = p[1]
                        val unit = parseAmount(p[2])
                        items += Row(c, d, unit * c)
                        return@forEach
                    }
                }

                // "cant x desc unit" -> subtotal = cant * unit
                Regex("""^\s*(\d+)\s*[xX]?\s*[-–]?\s*(.+?)\s+([$€]?\s*[-+]?\d+(?:[.,]\d{1,2})?)\s*$""")
                    .find(line)?.let { m ->
                        val c = m.groupValues[1].toIntOrNull() ?: 1
                        val d = m.groupValues[2].trim()
                        val unit = parseAmount(m.groupValues[3])
                        items += Row(c, d, unit * c)
                        return@forEach
                    }
            }

        // --- Medidas para impresora térmica 80mm (72mm útiles) ---
        val PAGE_WIDTH_PT = mmToPt(72f)         // ≈ 204 pt
        val MARGIN_PT = mmToPt(2f)              // ≈ 5 pt
        val CONTENT_W_PT = PAGE_WIDTH_PT - MARGIN_PT * 2

        // --- Columnas (suman el ancho útil) ---
        val cantColW = (CONTENT_W_PT * 0.15f).toInt() // 15%
        val impColW  = (CONTENT_W_PT * 0.25f).toInt() // 25%
        val descColW = CONTENT_W_PT - cantColW - impColW // 60%

        // --- Tipografías ---
        val titleTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val bodyTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        }
        val boldTP = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
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

        val titulo = "FACTURA - ${nombreMesa.uppercase()}"
        val fechaHora = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        val titleL = layout(titulo, titleTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        val dateL  = layout("Fecha: $fechaHora", bodyTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_OPPOSITE)

        val GAP = 4f
        val RULE_SP = 6f
        var totalH = 0f

        totalH += titleL.height + GAP + 1 + RULE_SP
        totalH += dateL.height + GAP

        if (items.isNotEmpty()) {
            val hCant = layout("Cant", boldTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val hDesc = layout("Descripción", boldTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            val hImp  = layout("Importe", boldTP, impColW, android.text.Layout.Alignment.ALIGN_OPPOSITE)
            totalH += maxOf(hCant.height, hDesc.height, hImp.height) + GAP + 1 + RULE_SP

            items.forEach {
                val cL = layout(it.cant.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
                val dL = layout(it.desc, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
                val iL = layout(money(it.rowTotal), bodyTP, impColW, android.text.Layout.Alignment.ALIGN_OPPOSITE)
                totalH += maxOf(cL.height, dL.height, iL.height) + GAP
            }
            totalH += 1 + RULE_SP
        }

        val subtotal = items.sumOf { it.rowTotal }
        val total    = subtotal

        val leftW = (CONTENT_W_PT * 0.5f).toInt()
        val rightW = CONTENT_W_PT - leftW
        val subL = layout("SUBTOTAL", boldTP, leftW, android.text.Layout.Alignment.ALIGN_NORMAL)
        val subV = layout(money(subtotal), boldTP, rightW, android.text.Layout.Alignment.ALIGN_OPPOSITE)
        val totL = layout("TOTAL", titleTP, leftW, android.text.Layout.Alignment.ALIGN_NORMAL)
        val totV = layout(money(total), titleTP, rightW, android.text.Layout.Alignment.ALIGN_OPPOSITE)

        totalH += maxOf(subL.height, subV.height) + GAP
        totalH += maxOf(totL.height, totV.height) + GAP

        val footL = layout("Gracias por su compra", smallTP, CONTENT_W_PT, android.text.Layout.Alignment.ALIGN_CENTER)
        totalH += footL.height

        val PAGE_HEIGHT_PT = (totalH + MARGIN_PT * 2).toInt().coerceAtLeast(mmToPt(90f))

        // --- Crear PDF y dibujar ---
        val file = File(getExternalFilesDir(null), "factura_${nombreMesa.replace(" ", "_")}.pdf")
        val doc = android.graphics.pdf.PdfDocument()
        val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, 1).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        var y = MARGIN_PT.toFloat()
        fun rule() {
            canvas.drawLine(MARGIN_PT.toFloat(), y, (PAGE_WIDTH_PT - MARGIN_PT).toFloat(), y, rulePaint)
            y += RULE_SP
        }

        // Título
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); titleL.draw(canvas); canvas.restore()
        y += titleL.height + GAP; rule()

        // Fecha
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); dateL.draw(canvas); canvas.restore()
        y += dateL.height + GAP

        // Tabla
        if (items.isNotEmpty()) {
            var x = MARGIN_PT.toFloat()
            val hCant = layout("Cant", boldTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
            val hDesc = layout("Descripción", boldTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
            val hImp  = layout("Importe", boldTP, impColW, android.text.Layout.Alignment.ALIGN_OPPOSITE)
            val hH = maxOf(hCant.height, hDesc.height, hImp.height)

            canvas.save(); canvas.translate(x, y); hCant.draw(canvas); canvas.restore()
            x += cantColW
            canvas.save(); canvas.translate(x, y); hDesc.draw(canvas); canvas.restore()
            x += descColW
            canvas.save(); canvas.translate(x, y); hImp.draw(canvas); canvas.restore()
            y += hH + GAP; rule()

            items.forEach {
                x = MARGIN_PT.toFloat()
                val cL = layout(it.cant.toString(), bodyTP, cantColW, android.text.Layout.Alignment.ALIGN_CENTER)
                val dL = layout(it.desc, bodyTP, descColW, android.text.Layout.Alignment.ALIGN_NORMAL)
                val iL = layout(money(it.rowTotal), bodyTP, impColW, android.text.Layout.Alignment.ALIGN_OPPOSITE)
                val rH = maxOf(cL.height, dL.height, iL.height)

                canvas.save(); canvas.translate(x, y); cL.draw(canvas); canvas.restore()
                x += cantColW
                canvas.save(); canvas.translate(x, y); dL.draw(canvas); canvas.restore()
                x += descColW
                canvas.save(); canvas.translate(x, y); iL.draw(canvas); canvas.restore()
                y += rH + GAP
            }
            rule()
        }

        // Subtotal / Total
        run {
            val subLx = MARGIN_PT.toFloat()
            val subVx = (MARGIN_PT + leftW).toFloat()

            val r1 = maxOf(subL.height, subV.height)
            canvas.save(); canvas.translate(subLx, y); subL.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(subVx, y); subV.draw(canvas); canvas.restore()
            y += r1 + GAP

            val r2 = maxOf(totL.height, totV.height)
            canvas.save(); canvas.translate(subLx, y); totL.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(subVx, y); totV.draw(canvas); canvas.restore()
            y += r2 + GAP
        }

        // Footer
        canvas.save(); canvas.translate(MARGIN_PT.toFloat(), y); footL.draw(canvas); canvas.restore()

        doc.finishPage(page)
        doc.writeTo(file.outputStream())
        doc.close()

        abrirPDF(file)
    }

    // Mostrar precios como enteros con separador de miles (sin decimales)
    private fun money(v: Double): String = "$" + java.text.DecimalFormat("#,##0").format(v)

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
