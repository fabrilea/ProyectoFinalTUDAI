package com.example.sistemadecomandas

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.sistemadecomandas.model.Pedido
import com.example.sistemadecomandas.model.Producto
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
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)

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

                val result = Intent().apply {
                    putExtra("mesa", nombreMesa)
                    putExtra("ocupada", true)
                    putExtra("pedidos_json", gson.toJson(pedidos))
                }
                prefs.edit().putString(nombreMesa, gson.toJson(acumulado)).apply()

                setResult(Activity.RESULT_OK, result)
                generarComandaPDF(pedidos)


                finish()
            }
        }

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
                    pedidos.add(Pedido(producto.nombre, producto.precio, picker.value, producto.categoria))
                }
                actualizarTotal()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun actualizarTotal() {
        contenedorSeleccionados.removeAllViews()
        var total = 0.0

        pedidos.sortedWith(compareBy({ it.categoria }, { it.nombre })).forEachIndexed { index, pedido ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(8, 8, 8, 8)
            }

            val txt = TextView(this).apply {
                text = "[${pedido.categoria}] ${pedido.nombre} x${pedido.cantidad} = $${pedido.precio * pedido.cantidad}"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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

    private fun generarComandaPDF(pedidos: List<Pedido>) {
        val fileName = "comanda_${nombreMesa.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = File(getExternalFilesDir(null), fileName)
        val document = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 600, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = android.graphics.Paint().apply { textSize = 12f }

        canvas.drawText("COMANDA - $nombreMesa", 10f, 20f, paint)

        var y = 40
        for (p in pedidos) {
            val line = "${p.nombre} x${p.cantidad} = $${p.precio * p.cantidad}"
            canvas.drawText(line, 10f, y.toFloat(), paint)
            y += 20
        }

        document.finishPage(page)
        document.writeTo(file.outputStream())
        document.close()

        // Guardar nombre del archivo en SharedPreferences para reimpresión
        getSharedPreferences("comandas_pdf", MODE_PRIVATE)
            .edit()
            .putString(nombreMesa, file.name)
            .apply()

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
