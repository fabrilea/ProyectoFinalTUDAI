package com.example.sistemadecomandas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.sistemadecomandas.model.Pedido
import com.example.sistemadecomandas.utils.RegistroPagos
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ResumenDiarioActivity : AppCompatActivity() {

    private lateinit var contenedorResumen: LinearLayout
    private lateinit var visorPdfLauncher: ActivityResultLauncher<Intent>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resumen_diario)

        contenedorResumen = findViewById(R.id.contenedorResumen)
        val btnConfirmar = findViewById<Button>(R.id.btnConfirmarCerrarDia)
        RegistroPagos.cargarDesdePreferencias(this)
        mostrarEstadisticas()

        btnConfirmar.setOnClickListener {
            cerrarDia()
        }

        visorPdfLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Cuando el usuario vuelve desde el visor de PDF
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

    }

    private fun mostrarEstadisticas() {

        val resumenPrefs = getSharedPreferences("resumen_dia", MODE_PRIVATE)
        val deliveryHistorial = getSharedPreferences("delivery_historial", MODE_PRIVATE)
        val gson = Gson()

        val jsonPedidos = resumenPrefs.getString("lista", null)
        val pedidos: List<Pedido> = if (!jsonPedidos.isNullOrEmpty()) {
            val tipoLista = object : TypeToken<List<Pedido>>() {}.type
            gson.fromJson(jsonPedidos, tipoLista)
        } else emptyList()

        val deliverySet = deliveryHistorial.getStringSet("historial", emptySet()) ?: emptySet()

        val productosVendidos = mutableMapOf<String, Int>()
        var totalIngresos = 0

        pedidos.forEach {
            val subtotal = it.precio * it.cantidad
            totalIngresos += subtotal
            productosVendidos[it.nombre] = productosVendidos.getOrDefault(it.nombre, 0) + it.cantidad
        }

        val resumenGeneral = listOf(
            "Resumen del Día",
            "",
            "Ingresos por mesas (productos): $${totalIngresos}",
            "",
            "Productos más vendidos:"
        )

        resumenGeneral.forEach {
            val txt = TextView(this)
            txt.text = it
            txt.textSize = 16f
            contenedorResumen.addView(txt)
        }

        productosVendidos.entries.sortedByDescending { it.value }.take(5).forEach {
            val txt = TextView(this)
            txt.text = "${it.key}: ${it.value} unidades"
            txt.textSize = 15f
            contenedorResumen.addView(txt)
        }

        // Pagos registrados
        val pagos = RegistroPagos.obtenerMapaCompleto()
        if (pagos.isNotEmpty()) {
            val tituloPagos = TextView(this)
            tituloPagos.text = "\nPagos registrados por mesa:"
            tituloPagos.textSize = 17f
            contenedorResumen.addView(tituloPagos)

            var totalPagado = 0
            for ((mesaHora, metodos) in pagos) {
                val txtMesa = TextView(this)
                txtMesa.text = "\n$mesaHora:"
                txtMesa.textSize = 16f
                contenedorResumen.addView(txtMesa)

                var totalMesa = 0
                for ((metodo, monto) in metodos) {
                    val txt = TextView(this)
                    txt.text = "  - $metodo: $$monto"
                    txt.textSize = 14f
                    contenedorResumen.addView(txt)
                    totalMesa += monto
                }

                val totalTxt = TextView(this)
                totalTxt.text = "  Total pagado en $mesaHora: $${totalMesa}"
                totalTxt.textSize = 14f
                contenedorResumen.addView(totalTxt)

                totalPagado += totalMesa
            }

            val txtTotal = TextView(this)
            txtTotal.text = "\nTotal pagado en mesas: $${totalPagado}"
            txtTotal.textSize = 16f
            contenedorResumen.addView(txtTotal)
        }

        // Mostrar deliveries
        if (deliverySet.isNotEmpty()) {
            val tituloDelivery = TextView(this)
            tituloDelivery.text = "\nDeliveries realizados:"
            tituloDelivery.textSize = 17f
            contenedorResumen.addView(tituloDelivery)

            var totalDelivery = 0
            deliverySet.forEach {
                val ultimoGuion = it.lastIndexOf("-$")
                if (ultimoGuion != -1) {
                    val nombre = it.substring(0, ultimoGuion)
                    val montoTexto = it.substring(ultimoGuion + 2)
                    val monto = montoTexto.toIntOrNull()
                    if (monto != null) {
                        totalDelivery += monto
                        val txt = TextView(this)
                        txt.text = "$nombre - $$monto"
                        txt.textSize = 14f
                        contenedorResumen.addView(txt)
                    }
                }
            }

            val totalDel = TextView(this)
            totalDel.text = "\nTotal por delivery: $${totalDelivery}"
            totalDel.textSize = 16f
            contenedorResumen.addView(totalDel)

            val totalFinal = TextView(this)
            totalFinal.text = "\nTotal final del día: $${(totalIngresos + totalDelivery)}"
            totalFinal.textSize = 17f
            contenedorResumen.addView(totalFinal)
        } else {
            val totalFinal = TextView(this)
            totalFinal.text = "\nTotal final del día: $${(totalIngresos)}"
            totalFinal.textSize = 17f
            contenedorResumen.addView(totalFinal)
        }

        if (pedidos.isEmpty() && deliverySet.isEmpty() && pagos.isEmpty()) {
            val txt = TextView(this)
            txt.text = "No hay datos registrados para el día."
            txt.textSize = 16f
            contenedorResumen.addView(txt)
        }

        val totalesPorMetodo = mutableMapOf<String, Int>()
        for ((_, metodos) in pagos) {
            for ((metodo, monto) in metodos) {
                totalesPorMetodo[metodo] = totalesPorMetodo.getOrDefault(metodo, 0) + monto
            }
        }

        val tituloMetodo = TextView(this)
        tituloMetodo.text = "\nTotales por método de pago:"
        tituloMetodo.textSize = 16f
        contenedorResumen.addView(tituloMetodo)

        for ((metodo, monto) in totalesPorMetodo) {
            val txt = TextView(this)
            txt.text = "- $metodo: $$monto"
            txt.textSize = 14f
            contenedorResumen.addView(txt)
        }

    }

    private fun generarPDFResumen(resumen: String) {
        val fileName = "resumen_dia.pdf"
        val file = File(getExternalFilesDir(null), fileName)
        val document = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(400, 600, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 12f }

        var y = 20
        resumen.split("\n").forEach {
            canvas.drawText(it, 10f, y.toFloat(), paint)
            y += 20
        }

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()
    }

    private fun abrirPDFResumen() {
        val file = File(getExternalFilesDir(null), "resumen_dia.pdf")
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            visorPdfLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el visor de PDF", Toast.LENGTH_SHORT).show()
            // Si falla, volvemos manualmente
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }
    }


    private fun construirResumenTexto(
        pedidos: List<Pedido>,
        deliverySet: Set<String>,
        pagos: Map<String, Map<String, Int>>
    ): String {
        val sb = StringBuilder()
        val productosVendidos = mutableMapOf<String, Int>()
        var totalIngresos = 0

        pedidos.forEach {
            val subtotal = it.precio * it.cantidad
            totalIngresos += subtotal
            productosVendidos[it.nombre] = productosVendidos.getOrDefault(it.nombre, 0) + it.cantidad
        }

        sb.append("Resumen del Día\n\n")
        sb.append("Ingresos por mesas (productos): $${totalIngresos}\n\n")
        sb.append("Productos más vendidos:\n")
        productosVendidos.entries.sortedByDescending { it.value }.take(5).forEach {
            sb.append("- ${it.key}: ${it.value} unidades\n")
        }

        var totalPagado = 0
        if (pagos.isNotEmpty()) {
            sb.append("\nPagos registrados por mesa:\n")
            for ((mesaHora, metodos) in pagos) {
                sb.append("\n$mesaHora:\n")
                var totalMesa = 0
                for ((metodo, monto) in metodos) {
                    sb.append("  - $metodo: $$monto\n")
                    totalMesa += monto
                }
                sb.append("  Total pagado: $${totalMesa}\n")
                totalPagado += totalMesa
            }
        }

        var totalDelivery = 0
        if (deliverySet.isNotEmpty()) {
            sb.append("\nDeliveries realizados:\n")
            deliverySet.forEach {
                val ultimoGuion = it.lastIndexOf("-$")
                if (ultimoGuion != -1) {
                    val nombre = it.substring(0, ultimoGuion)
                    val monto = it.substring(ultimoGuion + 2).toIntOrNull() ?: 0
                    sb.append("- $nombre: $$monto\n")
                    totalDelivery += monto
                }
            }
            sb.append("Total por delivery: $${totalDelivery}\n")
        }

        val totalFinal = totalIngresos + totalDelivery
        sb.append("\nTotal final del día: $${totalFinal}\n")

        return sb.toString()
    }



    private fun cerrarDia() {
        // Construir resumen antes de borrar datos
        val resumenPrefs = getSharedPreferences("resumen_dia", MODE_PRIVATE)
        val deliveryHistorial = getSharedPreferences("delivery_historial", MODE_PRIVATE)
        val gson = Gson()

        val jsonPedidos = resumenPrefs.getString("lista", null)
        val pedidos: List<Pedido> = if (!jsonPedidos.isNullOrEmpty()) {
            val tipoLista = object : TypeToken<List<Pedido>>() {}.type
            gson.fromJson(jsonPedidos, tipoLista)
        } else emptyList()

        val deliverySet = deliveryHistorial.getStringSet("historial", emptySet()) ?: emptySet()
        val pagos = RegistroPagos.obtenerMapaCompleto()

        val resumenTexto = construirResumenTexto(pedidos, deliverySet, pagos)
        generarPDFResumen(resumenTexto)
        generarPDFResumen(resumenTexto)
        val file = File(getExternalFilesDir(null), "resumen_dia.pdf")
        enviarResumenPorCorreo(file)


        Toast.makeText(this, "Día cerrado correctamente. Abriendo resumen...", Toast.LENGTH_SHORT)
            .show()
        abrirPDFResumen()



        // Limpieza
        getSharedPreferences("pedidos_mesas", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("estado_mesas", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("resumen_dia", MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences("delivery_historial", MODE_PRIVATE).edit().clear().apply()
        RegistroPagos.limpiar(this)

        // Borrar PDFs de delivery antiguos
        val directorio = getExternalFilesDir(null)
        directorio?.listFiles()?.forEach {
            if (it.name.startsWith("delivery_") && it.name.endsWith(".pdf")) {
                it.delete()
            }
        }
        
    }

    private fun enviarResumenPorCorreo(file: File) {
        val prefsCorreo = getSharedPreferences("email", MODE_PRIVATE)
        val correoDestino = prefsCorreo.getString("correo_resumen", null)


        if (!correoDestino.isNullOrEmpty() && file.exists()) {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(correoDestino))
                putExtra(Intent.EXTRA_SUBJECT, "Resumen del día")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el PDF con el resumen del día.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                startActivity(Intent.createChooser(intent, "Enviar resumen por correo..."))
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo enviar el correo", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Correo no configurado o archivo no encontrado", Toast.LENGTH_SHORT).show()
        }
    }


}
