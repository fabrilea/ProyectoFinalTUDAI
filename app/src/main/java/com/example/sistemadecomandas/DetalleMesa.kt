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
import com.example.sistemadecomandas.utils.RegistroPagos
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

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
                prefs.edit().putBoolean(nombreMesa, ocupada).apply()
                mostrarPedidos()
                verificarReimpresionComanda()
                setResult(RESULT_OK, Intent().apply {
                    putExtra("mesa", nombreMesa)
                    putExtra("ocupada", estaOcupada)
                })
            }
        }

        btnToggle.setOnClickListener {
            val intent = Intent(this, PedidoActivity::class.java)
            intent.putExtra("mesa", nombreMesa)
            intent.putExtra("ocupada", estaOcupada)
            resultadoPedido.launch(intent)
        }

        val prefsPedidos = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
        val jsonPedidos = prefsPedidos.getString(nombreMesa, null)

        btnCerrar.visibility = if (jsonPedidos.isNullOrEmpty()) View.GONE else View.VISIBLE

        btnCerrar.setOnClickListener {
            mostrarResumenYCerrar()
        }
    }

    private fun verificarReimpresionComanda() {
        btnReimprimir.visibility = if (!estaOcupada) View.GONE else View.VISIBLE

        if (!estaOcupada) return

        val directorio = getExternalFilesDir(null)
        val archivos = directorio?.listFiles()
        val ultimaComanda = archivos
            ?.filter { it.name.startsWith("comanda_${nombreMesa.replace(" ", "_")}") && it.name.endsWith(".pdf") }
            ?.maxByOrNull { it.lastModified() }

        if (ultimaComanda != null) {
            btnReimprimir.setOnClickListener {
                val uri = FileProvider.getUriForFile(this, "$packageName.provider", ultimaComanda)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir el visor de PDF", Toast.LENGTH_SHORT).show()
                }
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
        val resumen = construirResumenFactura(pedidos)
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

        val formato = DecimalFormat("#,##2.0")
        val totalStr = formato.format(total)
        val pagadoStr = formato.format(pagado)
        val pendienteStr = formato.format(pendiente)

        val resumenCompleto = buildString {
            appendLine("💰 Total:     $${totalStr}\n")
            appendLine("✅ Pagado:    $${pagadoStr}\n")
            appendLine("⏳ Pendiente: $${pendienteStr}\n")
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
                    val nuevoPendiente = pendiente - monto

                    if (nuevoPendiente <= 0) {
                        cerrarCuenta(resumen) // genera y abre la factura
                    } else {
                        Toast.makeText(this, "Pago parcial registrado. Restante: $${"%.2f".format(nuevoPendiente)}", Toast.LENGTH_LONG).show()
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



    private fun cerrarCuenta(resumenFactura: String) {
        val prefs = getSharedPreferences("pedidos_mesas", MODE_PRIVATE)
        val json = prefs.getString(nombreMesa, null)

        val tipoLista = object : TypeToken<List<Pedido>>() {}.type
        val pedidos = Gson().fromJson<List<Pedido>>(json, tipoLista)

        generarFacturaPDF(nombreMesa, resumenFactura)

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

    private fun guardarResumenComoArchivo(nombreMesa: String, resumen: String) {
        val timeStamp = java.text.SimpleDateFormat("HHmm", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "factura_${nombreMesa.replace(" ", "_")}_$timeStamp.pdf"
        openFileOutput(fileName, MODE_PRIVATE).use {
            it.write(resumen.toByteArray())
        }
        Toast.makeText(this, "Resumen guardado como $fileName", Toast.LENGTH_SHORT).show()
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
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se encontró visor de PDF", Toast.LENGTH_LONG).show()
        }
    }
}
