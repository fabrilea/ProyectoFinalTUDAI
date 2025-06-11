package com.example.sistemadecomandas

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.sistemadecomandas.model.Producto
import com.example.sistemadecomandas.repository.ProductoRepository

class ModificarProductos : AppCompatActivity() {

    private lateinit var spinnerCategorias: Spinner
    private lateinit var listaProductosLayout: LinearLayout
    private lateinit var btnAgregar: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modificar_productos)

        spinnerCategorias = findViewById(R.id.spinnerCategorias)
        listaProductosLayout = findViewById(R.id.listaProductos)
        btnAgregar = findViewById(R.id.btnAgregarProducto)

        actualizarSpinnerCategorias()

        spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, pos: Int, id: Long) {
                val categoria = parent.getItemAtPosition(pos).toString()
                mostrarProductos(categoria)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAgregar.setOnClickListener {
            mostrarDialogoAgregarProducto()
        }
    }

    private fun actualizarSpinnerCategorias() {
        val categorias = ProductoRepository.obtenerCategorias()
        spinnerCategorias.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
    }

    private fun mostrarProductos(categoria: String) {
        listaProductosLayout.removeAllViews()
        val productos = ProductoRepository.obtenerPorCategoria(categoria)
        for (producto in productos) {
            val fila = layoutInflater.inflate(R.layout.item_producto_editable, listaProductosLayout, false)

            fila.findViewById<TextView>(R.id.txtNombreProducto).text = producto.nombre
            fila.findViewById<TextView>(R.id.txtPrecioProducto).text = "$${producto.precio}"

            fila.findViewById<Button>(R.id.btnEliminar).setOnClickListener {
                ProductoRepository.eliminarProducto(producto.id)
                actualizarSpinnerCategorias()
                mostrarProductos(categoria)
            }

            fila.findViewById<Button>(R.id.btnEditar).setOnClickListener {
                mostrarDialogoEditarProducto(producto)
            }

            listaProductosLayout.addView(fila)
        }
    }

    private fun mostrarDialogoAgregarProducto() {
        val layout = layoutInflater.inflate(R.layout.dialogo_producto, null)
        val nombre = layout.findViewById<EditText>(R.id.inputNombre)
        val precio = layout.findViewById<EditText>(R.id.inputPrecio)
        val spinner = layout.findViewById<Spinner>(R.id.spinnerCategoria)

        val categorias = ProductoRepository.obtenerCategorias()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)

        AlertDialog.Builder(this)
            .setTitle("Nuevo producto")
            .setView(layout)
            .setPositiveButton("Agregar") { _, _ ->
                val categoriaSeleccionada = spinner.selectedItem.toString()
                ProductoRepository.agregarProducto(
                    nombre.text.toString(),
                    categoriaSeleccionada,
                    (precio.text.toString().toIntOrNull() ?: 0)
                )
                actualizarSpinnerCategorias()
                spinnerCategorias.setSelection(categorias.indexOf(categoriaSeleccionada))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoEditarProducto(producto: Producto) {
        val layout = layoutInflater.inflate(R.layout.dialogo_producto, null)
        val nombre = layout.findViewById<EditText>(R.id.inputNombre)
        val precio = layout.findViewById<EditText>(R.id.inputPrecio)
        val spinner = layout.findViewById<Spinner>(R.id.spinnerCategoria)

        val categorias = ProductoRepository.obtenerCategorias()
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias)
        val posicion = categorias.indexOf(producto.categoria)
        if (posicion >= 0) spinner.setSelection(posicion)

        nombre.setText(producto.nombre)
        precio.setText(producto.precio.toString())

        AlertDialog.Builder(this)
            .setTitle("Editar producto")
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                ProductoRepository.eliminarProducto(producto.id)
                ProductoRepository.agregarProducto(
                    nombre.text.toString(),
                    spinner.selectedItem.toString(),
                    precio.text.toString().toIntOrNull() ?: 0
                )
                actualizarSpinnerCategorias()
                spinnerCategorias.setSelection(spinner.selectedItemPosition)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
