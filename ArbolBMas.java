// g28 - Jorge Hermosilla 5727706 


import java.nio.ByteBuffer;
import java.io.RandomAccessFile;
import java.io.IOException;

public class ArbolBMas implements AutoCloseable {

    // 1. Manejo del Archivo en Disco . 
    private RandomAccessFile archivo; //NO es el archivo en si ,es el acceso de ese archivo en el disco 
    private String rutaArchivo;

    // 2. Datos de la Cabecera 
    private int P; // P 
    private int M; // Máximo de hijos de nodo interno 
    private int L; // Máximo de pares clave-posición en hoja 
    private int raiz; // Número de página de la raíz 
    private int cantidadPaginas; // Total de páginas en el archivo (incluyendo la cabecera)
    private int niveles; // Altura del árbol 
    private long cantidadClaves; // Total de pares (clave, posición) almacenados

    // 3. Contadores de Transferencia de E/S 
    private long paginasLeidas;
    private long paginasEscritas;

    //La clase interna hoja es para almacenar en la RAM de manera temporal una pagina del archivo indice.dat que se encuantra en el disco 
    public class Pagina { 
        int nroPagina; // Número de página en el archivo 
        int tipo; // 0 = Hoja, 1 = Interno
        int n; // Cantidad actual de claves
        int siguiente; // En hoja: nro de siguiente hoja (-1 si es la última). En interno: -1

        // Nodos Internos (tamano M+1 para soportar la clave extra durante el Split)
        int[] hijosInterno;
        long[] clavesInterno;

        // Hojas (tamaño L+1 para soportar el par extra durante el Split)
        long[] clavesHoja;
        long[] posicionesHoja;

        // Constructor para crear una página vacía en RAM
        public Pagina(int nroPagina, int tipo) {
            this.nroPagina = nroPagina;
            this.tipo = tipo;
            this.n = 0;
            this.siguiente = -1;

            if (tipo == 0) { // Hoja
                this.clavesHoja = new long[L + 1];
                this.posicionesHoja = new long[L + 1];
            } else { // Interno
                this.hijosInterno = new int[M + 1];
                this.clavesInterno = new long[M + 1];
            }
        }

        // Carga el contenido del objeto leyendo un arreglo de P bytes del disco
        public void bufferAPagina(byte[] arreglo, int nroPagina) {
            this.nroPagina = nroPagina;
            ByteBuffer buff = ByteBuffer.wrap(arreglo);

            this.tipo = buff.getInt(0);
            this.n = buff.getInt(4);
            this.siguiente = buff.getInt(8);

            if (this.tipo == 0) { // Hoja
                if (this.clavesHoja == null) {
                    this.clavesHoja = new long[L + 1];
                    this.posicionesHoja = new long[L + 1];
                }
                for (int i = 0; i < n; i++) {
                    this.clavesHoja[i] = buff.getLong(12 + 16 * i);
                    this.posicionesHoja[i] = buff.getLong(12 + 16 * i + 8);
                }
            } else if (this.tipo == 1) { // Interno
                if (this.hijosInterno == null) {
                    this.hijosInterno = new int[M + 1];
                    this.clavesInterno = new long[M + 1];
                }
                // Hijos en 12 + 4i 
                for (int i = 0; i <= n; i++) {
                    this.hijosInterno[i] = buff.getInt(12 + 4 * i);
                }
                // Claves en 12 + 4M + 8i 
                for (int i = 0; i < n; i++) {
                    this.clavesInterno[i] = buff.getLong(12 + 4 * M + 8 * i);
                }
            }
        }

        // Convierte esta página en un arreglo de P bytes listo para ser grabado a disco
        public byte[] paginaABytes() {
            ByteBuffer buff = ByteBuffer.allocate(P); // Crea un buffer limpio de P bytes 

            buff.putInt(0, this.tipo);
            buff.putInt(4, this.n);
            buff.putInt(8, this.siguiente);//ingresa los datos al buffer 

            if (this.tipo == 0) { // Hoja
                for (int i = 0; i < this.n; i++) {
                    buff.putLong(12 + 16 * i, this.clavesHoja[i]);
                    buff.putLong(12 + 16 * i + 8, this.posicionesHoja[i]);
                }
            } else if (this.tipo == 1) { // Interno
                // Escribir hijos en 12 + 4i
                for (int i = 0; i <= this.n; i++) {
                    buff.putInt(12 + 4 * i, this.hijosInterno[i]);
                }
                // Escribir claves en 12 + 4M + 8i
                for (int i = 0; i < this.n; i++) {
                    buff.putLong(12 + 4 * M + 8 * i, this.clavesInterno[i]);
                }
            }

            return buff.array();
        }
    }

    //Constructor para crear un arbol vacio Es privado por que solo abrir() o crear() lo puedeb usar  
    private ArbolBMas(RandomAccessFile archivo, String ruta, int P, int L, int M, int raiz, int cantidadPaginas,
            int niveles, long cantClaves) {
        this.archivo = archivo;
        this.rutaArchivo = ruta;
        this.P = P;
        this.L = L;
        this.M = M;
        this.raiz = raiz;
        this.cantidadPaginas = cantidadPaginas;
        this.niveles = niveles;
        this.cantidadClaves = cantClaves;

        this.paginasEscritas = 0;
        this.paginasLeidas = 0;
    }

    //Crea un arbol desde cero , creando un archivo indice.dat vacio en el disco  
    static ArbolBMas crear(String ruta, int tamPagina) throws IOException {
        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");
        archivo.setLength(0);

        int P = tamPagina;
        int L = ((P - 12) / 16);
        int M = ((P - 4) / 12);
        if (tamPagina < 40 || L < 2 || M < 3) {
            archivo.close();
            throw new TamPaginaInvalidoException();
        }

        ByteBuffer buffer = ByteBuffer.allocate(tamPagina);
        buffer.putInt(0x41454433); // MAGIC "AED3"
        buffer.putInt(1); // versión
        buffer.putInt(tamPagina); // P
        buffer.putInt(M); // M
        buffer.putInt(L); // L
        buffer.putInt(1); // raiz = 1
        buffer.putInt(2); // cantidadPaginas = 2 
        buffer.putInt(1); // niveles = 1
        buffer.putLong(0);

        byte[] cabecera = buffer.array();
        archivo.seek(0);// se pone en el archivo 0
        archivo.write(cabecera);// escribe la cabecera

        //Creamos la instancia REAL de ArbolBMas primero
        ArbolBMas arbol = new ArbolBMas(archivo, ruta, P, L, M, 1, 2, 1, 0);

        // Ahora creamos la Página 1 (Raíz Hoja Vacía) usando 'arbol'
        ArbolBMas.Pagina raizInicial = arbol.new Pagina(1, 0); // nroPagina=1, tipo=0 (Hoja)

        // Escribimos la Página 1 en la posición 1 * P del archivo
        archivo.seek(P);
        archivo.write(raizInicial.paginaABytes());

        // Retornamos el árbol creado
        return arbol;

    }

    public static ArbolBMas abrir(String ruta) throws IOException {
        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");
        archivo.seek(0);

        if (archivo.length() < 40) {
            archivo.close();
            throw new FormatoInvalidoException();
        }

        //saca los datos de la cabecera para validarlos 
        int magic = archivo.readInt();
        int version = archivo.readInt();
        int P = archivo.readInt();
        int M = archivo.readInt();
        int L = archivo.readInt();
        int raiz = archivo.readInt();
        int cant_paginas = archivo.readInt();
        int niveles = archivo.readInt();
        long cant_claves = archivo.readLong();
        if (magic != 0x41454433 || version != 1 || M != ((P - 4) / 12) || L != ((P - 12) / 16)
                || archivo.length() != (long) cant_paginas * P) {
            archivo.close();
            throw new FormatoInvalidoException();
        }

        //Si el formato del archivo es valido retorna el arbol 
        return (new ArbolBMas(archivo, ruta, P, L, M, raiz, cant_paginas, niveles, cant_claves));

    }

    //Clases privadas para leer o escribir una pagina 

    //Accede al disco, trae el stracto de bytes ylo convierte en una pagina 
    private Pagina leerPagina(int nmroPagina) throws IOException {
        archivo.seek((long) nmroPagina * P);// pone el cursor del archico en el comienzo de la pagina

        byte[] arraybytes = new byte[P];
        archivo.readFully(arraybytes);

        this.paginasLeidas++;

        Pagina pag = new Pagina(nmroPagina, 0);//el tipo en este caso no importa por que la funcion bufferApagina lo reescribe 
        pag.bufferAPagina(arraybytes, nmroPagina);

        return (pag);
    }

    //Toma la pagina del argumento y lo escribe en su posicion 
    private void escribirPagina(Pagina pag) throws IOException {
        long posicion = (long) P * pag.nroPagina;
        archivo.seek(posicion);

        this.paginasEscritas++;

        archivo.write(pag.paginaABytes());// escribe la pagina en el archivo

    }

    private void escribirCabecera() throws IOException {// actualiza la cabecera despues de un cambio
        ByteBuffer buffer = ByteBuffer.allocate(P);

        //Escribe un nuevo buffer con los datos de la clase actualizados 

        buffer.putInt(0x41454433); // MAGIC "AED3"
        buffer.putInt(1); // versión
        buffer.putInt(this.P); // P
        buffer.putInt(this.M); // M
        buffer.putInt(this.L); // L
        buffer.putInt(this.raiz); // Número de página de la raíz actual
        buffer.putInt(this.cantidadPaginas); // Total de páginas actuales
        buffer.putInt(this.niveles); // Altura actual
        buffer.putLong(this.cantidadClaves); // Cantidad total de claves

        archivo.seek(0);
        archivo.write(buffer.array());//convierte el buffer y lo escribe en la posicion de la cabecera 

    }

    public void close() throws IOException {
        if (archivo != null) {
            escribirCabecera();
            archivo.close();

        }

    }

    // Metodo buscar() y sus metodos auxiliares
    // Búsqueda binaria en nodo interno: encuentra el primer índice i tal que clave < clavesInterno[i]

    private int buscarIndiceHijo(Pagina pag, long clave) {
        int inicio = 0;
        int fin = pag.n - 1;
        int resultado = pag.n; // Si la clave es mayor o igual a todas, desciende por el último hijo

        while (inicio <= fin) {
            int medio = (inicio + fin) / 2;
            if (clave < pag.clavesInterno[medio]) {
                resultado = medio;
                fin = medio - 1; // Buscar si hay un límite más a la izquierda
            } else {
                inicio = medio + 1;
            }
        }
        return resultado;
    }

    // Búsqueda binaria en hoja: retorna la posición en el arreglo de la clave o -1 si no existe 
    private int buscarEnHoja(Pagina pag, long clave) {
        int inicio = 0;
        int fin = pag.n - 1;

        while (inicio <= fin) {
            int medio = (inicio + fin) / 2;
            if (pag.clavesHoja[medio] == clave) {
                return medio;
            } else if (pag.clavesHoja[medio] < clave) {
                inicio = medio + 1;
            } else {
                fin = medio - 1;
            }
        }
        return -1;
    }
    //metodo buscar() que busca en la pagina apicando busqueda binaria hasta llegar a la clave y retorna la posicion en la memoria 
    public long buscar(long clave) throws IOException {
        Pagina pag = leerPagina(this.raiz);

        // 1. Descender por los nodos internos usando Búsqueda Binaria
        while (pag.tipo == 1) {
            int idxHijo = buscarIndiceHijo(pag, clave);
            pag = leerPagina(pag.hijosInterno[idxHijo]);
        }

        // 2. Localizar la clave en la hoja alcanzada usando Búsqueda Binaria
        int idxClave = buscarEnHoja(pag, clave);
        if (idxClave != -1) {
            return pag.posicionesHoja[idxClave];
        }

        return -1L;
    }
    //=====getters de datos del arbol B+ =====
    public int niveles() {
        return (this.niveles);
    }

    public int raiz() {
        return (this.raiz);
    }

    public int cantidadPaginas() {
        return (this.cantidadPaginas);
    }

    public long cantidadClaves() {
        return (this.cantidadClaves);
    }

    public int tamPagina() {
        return (this.P);
    }

    public int ordenInterno() {
        return (this.M);
    }

    public int capacidadHoja() {
        return (this.L);
    }

    public long paginasLeidas() {
        return (this.paginasLeidas);
    }

    public long paginasEscritas() {
        return (this.paginasEscritas);
    }

    public void reiniciarContadores() {
        this.paginasEscritas = 0;
        this.paginasLeidas = 0;
    }

    public String volcarPagina(int nmroPagina) throws IOException {
        Pagina pag = leerPagina(nmroPagina);
        this.paginasLeidas--; // Descuenta la lectura para no alterar los contadores de rendimiento

        StringBuilder sb = new StringBuilder();
        sb.append("#").append(nmroPagina);

        if (pag.tipo == 0) { // Hoja
            sb.append(" H n=").append(pag.n).append(" sig= ").append(pag.siguiente).append(" [");
            for (int i = 0; i < pag.n; i++) {
                if (i > 0) sb.append(" ");
                sb.append(pag.clavesHoja[i]).append(":").append(pag.posicionesHoja[i]);
            }
            sb.append("]");
        } else { // Interno
            sb.append(" I n=").append(pag.n).append(" [");
            for (int i = 0; i < pag.n; i++) {
                sb.append("#").append(pag.hijosInterno[i]).append(" ").append(pag.clavesInterno[i]).append(" ");
            }
            sb.append("#").append(pag.hijosInterno[pag.n]).append("]");
        }

        return sb.toString();
    }

    // =====metodo insertar , clase y metodos auxiliares=======

    private static class Promocion {
        long clave;
        int nuevaPagina;

        public Promocion(long clave, int nuevaPagina) {
            this.clave = clave;
            this.nuevaPagina = nuevaPagina;
        }
    }

    private static final Promocion DUPLICADO = new Promocion(-1, -1);

    //metodo para insertar una nueva clave y su posicion en una pagina hoja de manera ordenada 
    private void insertarEnHojaOrdenado(Pagina pag, long clave, long posicion) {
        int i = pag.n - 1;

        // Desplazar elementos hacia la derecha mientras sean mayores que la nueva clave
        while (i >= 0 && pag.clavesHoja[i] > clave) {
            pag.clavesHoja[i + 1] = pag.clavesHoja[i];
            pag.posicionesHoja[i + 1] = pag.posicionesHoja[i];
            i--;
        }

        // Insertar la clave y la posición en el hueco resultante
        pag.clavesHoja[i + 1] = clave;
        pag.posicionesHoja[i + 1] = posicion;

        // Incrementar la cantidad de elementos almacenados en la página
        pag.n++;
    }
    /**
     * Inserta una clave separadora promovida y el puntero al nuevo hijo en un nodo interno de forma ordenada.
     * Misma logica que el anterior pero en una hoja 
     */
    private void insertarEnInternoOrdenado(Pagina pag, long clave, int nuevoHijo) {
        int i = pag.n - 1;
        while (i >= 0 && pag.clavesInterno[i] > clave) {
            pag.clavesInterno[i + 1] = pag.clavesInterno[i];
            pag.hijosInterno[i + 2] = pag.hijosInterno[i + 1];
            i--;
        }
        pag.clavesInterno[i + 1] = clave;
        pag.hijosInterno[i + 2] = nuevoHijo;
        pag.n++;
    }

    /**
     * Divide una hoja desbordada (L + 1 elementos) en dos hojas de tamaño similar.
     * Actualiza la cadena de hojas y escribe ambas páginas a disco.
     */
    private Promocion dividirHoja(Pagina hoja) throws IOException {
        int corte = (L + 2) / 2; 
        int numNuevos = (L + 1) - corte;

        int nroNueva = this.cantidadPaginas;//numero de pagina de la nueva hoja
        this.cantidadPaginas++;//aumenta la cantidad de paginas por la nueva pagina que va a ser insertada

        Pagina hojaNueva = new Pagina(nroNueva, 0); // Tipo 0 = Hoja

        // Copiar la segunda mitad de los pares a la hoja nueva
        for (int i = 0; i < numNuevos; i++) {
            hojaNueva.clavesHoja[i] = hoja.clavesHoja[corte + i];
            hojaNueva.posicionesHoja[i] = hoja.posicionesHoja[corte + i];
        }
        hojaNueva.n = numNuevos;
        hoja.n = corte;

        // Actualizar la cadena secuencial de hojas
        hojaNueva.siguiente = hoja.siguiente;
        hoja.siguiente = nroNueva;

        // Grabar ambas páginas en disco
        escribirPagina(hojaNueva);
        escribirPagina(hoja);

        // Retornar la primera clave de la hoja nueva (copiada) como separador
        return new Promocion(hojaNueva.clavesHoja[0], nroNueva);
    }

    /**
     * Divide un nodo interno desbordado (M claves y M + 1 hijos).
     * La clave mediana sube al nivel superior y desaparece de este nivel.
     */
    private Promocion dividirInterno(Pagina interno) throws IOException {
        int numHijosIzq = (M + 2) / 2; 
        int numHijosDer = (M + 1) - numHijosIzq;
        int numClavesDer = M - numHijosIzq;

        int nroNuevo = this.cantidadPaginas;//numero de pagina del nuevo interno 
        this.cantidadPaginas++;

        Pagina nuevoInterno = new Pagina(nroNuevo, 1); // Tipo 1 = Interno

        // Clave mediana que sube y se elimina del nivel actual
        long clavePromovida = interno.clavesInterno[numHijosIzq - 1];

        // Trasladar hijos y claves correspondientes al nuevo nodo interno derecho
        for (int i = 0; i < numHijosDer; i++) {
            nuevoInterno.hijosInterno[i] = interno.hijosInterno[numHijosIzq + i];
        }
        for (int i = 0; i < numClavesDer; i++) {
            nuevoInterno.clavesInterno[i] = interno.clavesInterno[numHijosIzq + i];
        }

        nuevoInterno.n = numClavesDer;
        interno.n = numHijosIzq - 1;

        // Grabar ambas páginas en disco
        escribirPagina(nuevoInterno);
        escribirPagina(interno);

        return new Promocion(clavePromovida, nroNuevo);
    }

    /**
     * Inserción recursiva hacia abajo con propagación de divisiones hacia arriba.
     */
    private Promocion insertarRec(int nroPagina, long clave, long posicion) throws IOException {
        Pagina pag = leerPagina(nroPagina);

        if (pag.tipo == 0) { // CASO BASE: Nodo Hoja
            // 1. Validar si la clave ya existe
            if (buscarEnHoja(pag, clave) != -1) {
                return DUPLICADO;
            }

            // 2. Insertar temporalmente el elemento manteniendo el orden
            insertarEnHojaOrdenado(pag, clave, posicion);

            // 3. Evaluar si hubo desborde
            if (pag.n <= L) {
                escribirPagina(pag);//confirma la insercion temporal 
                return null; // Inserción limpia sin split
            } else {
                return dividirHoja(pag); // Split de hoja
            }
        } else { // CASO RECURSIVO: Nodo Interno
            //  Determinar el hijo por el que se debe descender mediante Búsqueda Binaria
            int idxHijo = buscarIndiceHijo(pag, clave);

            //  Descender recursivamente
            Promocion promo = insertarRec(pag.hijosInterno[idxHijo], clave, posicion);

            //  Evaluar la respuesta de la llamada descendente
            if (promo == DUPLICADO) {
                return DUPLICADO;
            }
            if (promo == null) {
                return null; // El hijo absorbió el elemento sin dividirse
            }

            //  El hijo se dividió: insertar el separador y la nueva página en este nodo interno
            insertarEnInternoOrdenado(pag, promo.clave, promo.nuevaPagina);

            //  Evaluar desborde del nodo interno
            if (pag.n <= M - 1) { // M - 1 es el máximo de claves permitidas en un interno
                escribirPagina(pag);
                return null; // El nodo interno absorbió la clave promovida sin dividirse
            } else {
                return dividirInterno(pag); // Split de interno
            }
        }
    }//Notese que si la funcion llamada desde la raiz retorna una promocion es por que hay que hacer un split de raiz

    /**
     * Método público principal para la inserción de claves en el Árbol B+.
     *
     * @param clave    Clave a insertar.
     * @param posicion Posición del registro en el archivo de datos.
     * @return true si la clave fue insertada con éxito; false si ya existía.
     */
    public boolean insertar(long clave, long posicion) throws IOException {
        Promocion promo = insertarRec(this.raiz, clave, posicion);

        // Si la clave era duplicada, retornar false sin alterar el archivo
        if (promo == DUPLICADO) {
            return false;
        }

        // Si la división alcanzó la raíz anterior, crear una nueva raíz
        if (promo != null) {
            int nuevaPagRaiz = this.cantidadPaginas;//numero de pagina de la nueva raiz
            this.cantidadPaginas++;

            Pagina nuevaRaiz = new Pagina(nuevaPagRaiz, 1); // Tipo Interno
            nuevaRaiz.n = 1;
            nuevaRaiz.hijosInterno[0] = this.raiz;
            nuevaRaiz.hijosInterno[1] = promo.nuevaPagina;
            nuevaRaiz.clavesInterno[0] = promo.clave;

            escribirPagina(nuevaRaiz);

            // Actualizar los metadatos globales del árbol
            this.raiz = nuevaPagRaiz;
            this.niveles++;
        }

        // Actualizar metadatos globales de cabecera y guardar
        this.cantidadClaves++;
        escribirCabecera();
        return true;
    }


    public int[] caminoBusqueda(long clave) throws IOException {
        int[] camino = new int[this.niveles];
        int idx = 0;

        Pagina pag = leerPagina(this.raiz);
        camino[idx++] = pag.nroPagina;

        while (pag.tipo == 1) {
            int idxHijo = buscarIndiceHijo(pag, clave);
            pag = leerPagina(pag.hijosInterno[idxHijo]);
            camino[idx++] = pag.nroPagina;
        }

        return camino;
    }

    //==== metodo de bucarRando()======

    /**
     * Busca todas las entradas cuyas claves se encuentren en el rango [desde, hasta].
     * Recorre la cadena secuencial de hojas y entrega los pares al consumidor 'c'.
     *
     * @param desde Clave inicial del rango (inclusive).
     * @param hasta Clave final del rango (inclusive).
     * @param c     Consumidor funcional para procesar cada par hallado.
     * @return La cantidad total de pares entregados.
     * @throws RangoInvalidoException Si desde > hasta.
     * @throws IOException            Si ocurre un error de E/S en disco.
     */
    public int buscarRango(long desde, long hasta, ConsumidorEntrada c) throws IOException, RangoInvalidoException {
        if (desde > hasta) {
            throw new RangoInvalidoException();
        }

        //  Descender una sola vez a la hoja donde debería ubicarse 'desde' usando Búsqueda Binaria
        Pagina pag = leerPagina(this.raiz);
        while (pag.tipo == 1) {
            int idxHijo = buscarIndiceHijo(pag, desde);
            pag = leerPagina(pag.hijosInterno[idxHijo]);
        }

        int totalEntregados = 0;

        // Recorrer la cadena de hojas en orden ascendente
        while (pag != null) {
            // Procesar las claves de la hoja actual dentro del rango [desde, hasta]
            for (int i = 0; i < pag.n; i++) {
                long k = pag.clavesHoja[i];
                if (k > hasta) {
                    return totalEntregados; // Detener inmediatamente si se supera 'hasta'
                }
                if (k >= desde) {
                    c.aceptar(k, pag.posicionesHoja[i]);
                    totalEntregados++;
                }
            }

            // REGLA DE PARADA: Avanza a la siguiente hoja SOLO SI la última clave de la actual es < hasta
            if (pag.n == 0 || pag.clavesHoja[pag.n - 1] >= hasta || pag.siguiente == -1) {
                break; // Se detiene sin leer la siguiente página de hoja
            }

            // Cargar la siguiente hoja ligada
            pag = leerPagina(pag.siguiente);
        }

        return totalEntregados;
    }

    //=====VolcarArbol y su auxiliar=====
    /**
     * Auxiliar recursivo para volcar únicamente los nodos pertenecientes a 'nivelObjetivo'.
     * No altera los contadores de transferencia E/S del árbol.
     */
    private void volcarNivelRec(int nroPagina, int nivelObjetivo, int nivelActual, StringBuilder sb, boolean[] esPrimero) throws IOException {
        Pagina pag = leerPagina(nroPagina);
        this.paginasLeidas--; // Recompensa la lectura para que las mediciones permanezcan intactas

        if (nivelActual == nivelObjetivo) {
            if (!esPrimero[0]) {
                sb.append(" | ");
            } else {
                esPrimero[0] = false;
            }

            sb.append("#").append(pag.nroPagina);
            if (pag.tipo == 0) { // Hoja
                sb.append("[");
                for (int i = 0; i < pag.n; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(pag.clavesHoja[i]);
                }
                sb.append("]");
            } else { // Interno
                sb.append("(");
                for (int i = 0; i < pag.n; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(pag.clavesInterno[i]);
                }
                sb.append(")");
            }
        } else if (pag.tipo == 1) { // Nodo Interno: descender a los subárboles hijos
            for (int i = 0; i <= pag.n; i++) {
                volcarNivelRec(pag.hijosInterno[i], nivelObjetivo, nivelActual + 1, sb, esPrimero);
            }
        }
    }

    /**
     * Devuelve una representación en String del árbol B+, una línea por nivel.
     */
    public String volcarArbol() throws IOException {
        StringBuilder sb = new StringBuilder();

        for (int lvl = 1; lvl <= this.niveles; lvl++) {
            if (lvl > 1) {
                sb.append("\n");
            }
            sb.append(lvl).append(": ");
            boolean[] esPrimero = new boolean[]{true};
            volcarNivelRec(this.raiz, lvl, 1, sb, esPrimero);
        }

        return sb.toString();
    }
    
    //metodos para verificar la invariante
    /**
     * Recorre el árbol e inspecciona todas las invariantes de la estructura.
     *
     * @return "OK" si todas las propiedades se cumplen, o una cadena describiendo la falla.
     */
    public String verificarInvariantes() {
        try {
            // 1. Tamaño físico de archivo
            if (archivo.length() != (long) cantidadPaginas * P) {
                return "Invariante violada: El tamaño del archivo no coincide con cantidadPaginas * P";
            }

            boolean[] alcanzadas = new boolean[cantidadPaginas];
            int[] contadorClaves = new int[1];
            int[] ultimaHoja = new int[]{-1};

            String res = verificarRec(this.raiz, 1, Long.MIN_VALUE, Long.MAX_VALUE, alcanzadas, contadorClaves, ultimaHoja);
            if (!"OK".equals(res)) {
                return res;
            }

            // 2. Coincidencia del total de claves con la cabecera
            if (contadorClaves[0] != this.cantidadClaves) {
                return "Invariante violada: La cantidad de claves no coincide con la cabecera";
            }

            // 3. Verificación de que cada página 1..cantidadPaginas-1 sea alcanzable exactamente una vez
            for (int i = 1; i < cantidadPaginas; i++) {
                if (!alcanzadas[i]) {
                    return "Invariante violada: La página #" + i + " no es alcanzable desde la raíz";
                }
            }

            return "OK";
        } catch (IOException e) {
            return "Error de E/S al verificar invariantes: " + e.getMessage();
        }
    }

    private String verificarRec(int nroPagina, int nivelActual, long minVal, long maxVal,
                                boolean[] alcanzadas, int[] contadorClaves, int[] ultimaHoja) throws IOException {
        if (nroPagina <= 0 || nroPagina >= cantidadPaginas) {
            return "Invariante violada: Número de página fuera de rango #" + nroPagina;
        }
        if (alcanzadas[nroPagina]) {
            return "Invariante violada: La página #" + nroPagina + " es alcanzada más de una vez (ciclo/referencia compartida)";
        }
        alcanzadas[nroPagina] = true;

        Pagina pag = leerPagina(nroPagina);
        this.paginasLeidas--; // Operación de diagnóstico: no altera contadores

        if (pag.tipo == 0) { // HOJA
            if (nivelActual != this.niveles) {
                return "Invariante I1 violada: Hoja #" + nroPagina + " no está en el nivel final (" + nivelActual + " vs " + niveles + ")";
            }
            if (nroPagina != this.raiz) {
                int minPares = (L + 1) / 2;
                if (pag.n < minPares || pag.n > L) {
                    return "Invariante I3 violada: Ocupación de hoja #" + nroPagina + " fuera de rango [" + minPares + ", " + L + "]";
                }
            }
            for (int i = 0; i < pag.n; i++) {
                long k = pag.clavesHoja[i];
                if (k < minVal || k >= maxVal) {
                    return "Invariante I2 violada: Clave " + k + " en hoja #" + nroPagina + " viola cotas [" + minVal + ", " + maxVal + ")";
                }
                if (i > 0 && pag.clavesHoja[i - 1] >= k) {
                    return "Invariante I1 violada: Claves desordenadas/duplicadas en hoja #" + nroPagina;
                }
            }
            contadorClaves[0] += pag.n;

            // Validar la cadena secuencial
            if (ultimaHoja[0] != -1) {
                Pagina ant = leerPagina(ultimaHoja[0]);
                this.paginasLeidas--;
                if (ant.siguiente != nroPagina) {
                    return "Invariante I4 violada: Puntero 'siguiente' incorrecto entre la hoja #" + ultimaHoja[0] + " y #" + nroPagina;
                }
            }
            ultimaHoja[0] = nroPagina;

        } else { // INTERNO
            if (nroPagina != this.raiz) {
                int minHijos = (M + 1) / 2;
                if (pag.n + 1 < minHijos || pag.n > M - 1) {
                    return "Invariante I3 violada: Ocupación de nodo interno #" + nroPagina + " fuera de rango";
                }
            } else {
                if (pag.n < 1 || pag.n > M - 1) {
                    return "Invariante I3 violada: Raíz interna con cantidad de claves inválida (" + pag.n + ")";
                }
            }

            for (int i = 0; i <= pag.n; i++) {
                long cotaInf = (i == 0) ? minVal : pag.clavesInterno[i - 1];
                long cotaSup = (i == pag.n) ? maxVal : pag.clavesInterno[i];

                String res = verificarRec(pag.hijosInterno[i], nivelActual + 1, cotaInf, cotaSup, alcanzadas, contadorClaves, ultimaHoja);
                if (!"OK".equals(res)) {
                    return res;
                }
            }
        }

        return "OK";
    }


    public class EstadisticasBMas {
        public int hojas;
        public int internos;
        public double ocupacionHojas;
        public double ocupacionInternos;

        public EstadisticasBMas(int hojas, int internos, double ocupacionHojas, double ocupacionInternos) {
            this.hojas = hojas;
            this.internos = internos;
            this.ocupacionHojas = ocupacionHojas;
            this.ocupacionInternos = ocupacionInternos;
        }
    }
    public EstadisticasBMas estadisticas() throws IOException {
        int[] contadores = new int[4]; // [0] = hojas, [1] = clavesHoja, [2] = internos, [3] = hijosInterno
        estadisticasRec(this.raiz, contadores);

        int hojas = contadores[0];
        int internos = contadores[2];

        double ocupacionHojas = (hojas == 0) ? 0.0 : (double) contadores[1] / (hojas * L);
        double ocupacionInternos = (internos == 0) ? 0.0 : (double) contadores[3] / (internos * M);

        return new EstadisticasBMas(hojas, internos, ocupacionHojas, ocupacionInternos);
    }

    private void estadisticasRec(int nroPagina, int[] contadores) throws IOException {
        Pagina pag = leerPagina(nroPagina);
        this.paginasLeidas--; // Operación de diagnóstico: no afecta contadores de E/S

        if (pag.tipo == 0) {
            contadores[0]++;
            contadores[1] += pag.n;
        } else {
            contadores[2]++;
            contadores[3] += (pag.n + 1);
            for (int i = 0; i <= pag.n; i++) {
                estadisticasRec(pag.hijosInterno[i], contadores);
            }
        }
    }

}