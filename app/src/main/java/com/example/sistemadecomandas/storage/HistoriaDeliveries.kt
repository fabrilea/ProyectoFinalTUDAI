package com.example.sistemadecomandas.storage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class HistorialDeliveries : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Historial de Deliveries"
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val directorio = getExternalFilesDir(null)
        val archivos = directorio?.listFiles()?.filter {
            it.name.startsWith("delivery_") && it.name.endsWith(".pdf")
        }?.sortedByDescending { it.lastModified() }

        if (archivos.isNullOrEmpty()) {
            layout.addView(TextView(this).apply {
                text = "No hay deliveries registrados."
            })
        } else {
            archivos.forEach { archivo ->
                val nombre = archivo.name
                val partes = nombre.removeSuffix(".pdf").split("_")
                val cliente = partes[1]
                val hora = partes.getOrNull(3)?.replace("-", ":") ?: "desconocida"

                val btn = Button(this).apply {
                    text = "Cliente: $cliente - Hora: $hora"
                    setOnClickListener {
                        val uri: Uri = FileProvider.getUriForFile(
                            this@HistorialDeliveries,
                            "$packageName.provider",
                            archivo
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@HistorialDeliveries, "No se pudo abrir el PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                layout.addView(btn)
            }
        }

        val scroll = ScrollView(this)
        scroll.addView(layout)
        setContentView(scroll)
    }
}
