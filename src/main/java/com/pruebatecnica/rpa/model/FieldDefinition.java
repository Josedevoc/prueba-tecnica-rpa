package com.pruebatecnica.rpa.model;

public class FieldDefinition {

    private int numero;
    private String nombre;
    private String tipo;
    private int enteros;
    private int decimales;
    private int longitud;
    private boolean obligatorio;
    private String regla;
    private String listaRef;
    private String defaultVal;
    private String datosAceptados;
    private int inicio;
    private int fin;

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public int getEnteros() { return enteros; }
    public void setEnteros(int enteros) { this.enteros = enteros; }

    public int getDecimales() { return decimales; }
    public void setDecimales(int decimales) { this.decimales = decimales; }

    public int getLongitud() { return longitud; }
    public void setLongitud(int longitud) { this.longitud = longitud; }

    public boolean isObligatorio() { return obligatorio; }
    public void setObligatorio(boolean obligatorio) { this.obligatorio = obligatorio; }

    public String getRegla() { return regla; }
    public void setRegla(String regla) { this.regla = regla; }

    public String getListaRef() { return listaRef; }
    public void setListaRef(String listaRef) { this.listaRef = listaRef; }

    public String getDefaultVal() { return defaultVal; }
    public void setDefaultVal(String defaultVal) { this.defaultVal = defaultVal; }

    public String getDatosAceptados() { return datosAceptados; }
    public void setDatosAceptados(String datosAceptados) { this.datosAceptados = datosAceptados; }

    public int getInicio() { return inicio; }
    public void setInicio(int inicio) { this.inicio = inicio; }

    public int getFin() { return fin; }
    public void setFin(int fin) { this.fin = fin; }

    public boolean isNumerico() {
        return "NUMERICO".equalsIgnoreCase(tipo);
    }

    public boolean tieneDecimales() {
        return decimales > 0;
    }

    @Override
    public String toString() {
        return String.format("Field[%d] %s (%s, len=%d, pos=%d-%d)",
                numero, nombre, tipo, longitud, inicio, fin);
    }
}
