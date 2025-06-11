package com.example.sistemadecomandas

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class Email : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email)

        val inputCorreo = findViewById<EditText>(R.id.edtCorreo)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarCorreo)

        // Cargar valor guardado previamente (si existe)
        val prefs = getSharedPreferences("email", MODE_PRIVATE)
        val correoGuardado = prefs.getString("correo_resumen", "")
        inputCorreo.setText(correoGuardado)

        btnGuardar.setOnClickListener {
            val correo = inputCorreo.text.toString().trim()
            if (correo.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                prefs.edit().putString("correo_resumen", correo).apply()
                Toast.makeText(this, "Correo guardado correctamente", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Correo inválido", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
