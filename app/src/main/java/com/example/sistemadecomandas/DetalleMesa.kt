package com.example.sistemadecomandas

import android.content.Intent
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
        val fileName = "factura_${nombreMesa.replace(" ", "_")}.pdf"
        val file = File(getExternalFilesDir(null), fileName)
        val document = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 12f }

        resumen.split("\n").forEachIndexed { index, line ->
            canvas.drawText(line, 10f, (20 + index * 20).toFloat(), paint)
        }

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()

        abrirPDF(file)
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
