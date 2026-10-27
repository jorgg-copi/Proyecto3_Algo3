// g28 - Jorge Hermosilla 5727706 
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.util.Random;

public class BMasApp {

    private static final String RUTA_INDICE = "indice.dat";
    private static final String RUTA_DATOS = "datos.dat";

    public static void main(String[] args) {
        if (args.length == 0) {
            imprimirUso();
            return;
        }

        String comando = args[0].toLowerCase();

        try {
            switch (comando) {
                case "construir":
                    ejecutarConstruir(args);
                    break;
                case "buscar":
                    ejecutarBuscar(args);
                    break;
                case "rango":
                    ejecutarRango(args);
                    break;
                case "secuencial":
                    ejecutarSecuencial(args);
                    break;
                case "info":
                    ejecutarInfo();
                    break;
                case "verificar":
                    ejecutarVerificar();
                    break;
                case "pagina":
                    ejecutarPagina(args);
                    break;
                default:
                    System.out.println("Comando no reconocido: " + comando);
                    imprimirUso();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error al ejecutar el comando '" + comando + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 
    // COMANDO 1: construir <archivo.tsv> [filas 300000] [tamPagina 4096] [--aleatorio]
    // 
    private static void ejecutarConstruir(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: java BMasApp construir <archivo.tsv> [filas 300000] [tamPagina 4096] [--aleatorio]");
            return;
        }

        String rutaTsv = args[1];
        int maxFilas = 300000;
        int tamPagina = 4096;
        boolean aleatorio = false;

        // Parseo de argumentos opcionales
        for (int i = 2; i < args.length; i++) {
            if ("--aleatorio".equalsIgnoreCase(args[i])) {
                aleatorio = true;
            } else {
                try {
                    int val = Integer.parseInt(args[i]);
                    if (val >= 40 && val <= 65536 && (val % 2 == 0)) {
                        tamPagina = val;
                    } else if (val > 0) {
                        maxFilas = val;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        File fileTsv = new File(rutaTsv);
        if (!fileTsv.exists()) {
            System.err.println("Error: El archivo '" + rutaTsv + "' no existe.");
            return;
        }

        System.out.println("=================================================================");
        System.out.println("                   CONSTRUCCION DEL INDICE                       ");
        System.out.println("=================================================================");
        System.out.println("TSV Origen    : " + rutaTsv);
        System.out.println("Filas Máximas : " + maxFilas);
        System.out.println("Tamaño Página : " + tamPagina + " bytes");
        System.out.println("Modo Inserción: " + (aleatorio ? "Aleatorio (Fisher-Yates)" : "En orden del TSV"));
        System.out.println("-----------------------------------------------------------------");

        long tiempoInicio = System.currentTimeMillis();

        // 1. Crear los archivos físicos
        ArchivoDatos archivoDatos = ArchivoDatos.crear(RUTA_DATOS);
        ArbolBMas arbol = ArbolBMas.crear(RUTA_INDICE, tamPagina);

        // Arreglos temporales para cargar filas
        long[] clavesTemp = new long[maxFilas];
        long[] posTemp = new long[maxFilas];
        int contRegistros = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(fileTsv))) {
            String linea = br.readLine(); // Saltear línea de encabezado si existe
            if (linea != null && linea.startsWith("tconst")) {
                linea = br.readLine();
            }

            while (linea != null && contRegistros < maxFilas) {
                if (!linea.trim().isEmpty()) {
                    String[] campos = linea.split("\t");
                    if (campos.length > 0) {
                        long clave = parsearClave(campos[0]);
                        long pos = archivoDatos.agregar(clave, linea);

                        clavesTemp[contRegistros] = clave;
                        posTemp[contRegistros] = pos;
                        contRegistros++;
                    }
                }
                linea = br.readLine();
            }
        }

        // 2. Si se solicitó --aleatorio, barajar con Fisher-Yates y semilla 2026
        if (aleatorio) {
            Random rng = new Random(2026);
            for (int i = contRegistros - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);

                long tmpClave = clavesTemp[i];
                clavesTemp[i] = clavesTemp[j];
                clavesTemp[j] = tmpClave;

                long tmpPos = posTemp[i];
                posTemp[i] = posTemp[j];
                posTemp[j] = tmpPos;
            }
        }

        // 3. Insertar todas las entradas en el Árbol B+
        for (int i = 0; i < contRegistros; i++) {
            arbol.insertar(clavesTemp[i], posTemp[i]);
        }

        long tiempoFin = System.currentTimeMillis();

        // Cierre de recursos
        archivoDatos.close();
        arbol.close();

        System.out.println("Construcción completada exitosamente.");
        System.out.println("Registros Insertados: " + contRegistros);
        System.out.println("Tiempo Transcurrido : " + (tiempoFin - tiempoInicio) + " ms");
        System.out.println("=================================================================");
    }

    // 
    // COMANDO 2: buscar <tt0111161 o 111161>
    // 
    private static void ejecutarBuscar(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: java BMasApp buscar <tt0111161 o 111161>");
            return;
        }

        long clave = parsearClave(args[1]);

        try (ArbolBMas arbol = ArbolBMas.abrir(RUTA_INDICE);
             ArchivoDatos datos = ArchivoDatos.abrir(RUTA_DATOS)) {

            
            int[] camino = arbol.caminoBusqueda(clave);
            arbol.reiniciarContadores();
            long pos = arbol.buscar(clave);
            long lecturasIndex = arbol.paginasLeidas();

            // Imprimir el camino de páginas recorridas
            StringBuilder sbCamino = new StringBuilder();
            sbCamino.append("paginas leidas: ").append(lecturasIndex).append(" (");
            for (int i = 0; i < camino.length; i++) {
                if (i > 0) sbCamino.append(" -> ");
                sbCamino.append("#").append(camino[i]);
            }
            sbCamino.append(")");
            System.out.println(sbCamino.toString());

            if (pos == -1L) {
                System.out.println("Clave " + args[1] + " ( numeric " + clave + " ) no encontrada.");
            } else {
                String lineaRegistro = datos.leer(pos);
                System.out.println("pos=" + pos + " " + lineaRegistro);
            }
        }
    }

    // 
    // COMANDO 3: rango <desde> <hasta> [mostrar=10]
    // 
    private static void ejecutarRango(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Uso: java BMasApp rango <desde> <hasta> [mostrar=10]");
            return;
        }

        long desde = parsearClave(args[1]);
        long hasta = parsearClave(args[2]);
        int limiteMostrar = (args.length > 3) ? Integer.parseInt(args[3]) : 10;

        try (ArbolBMas arbol = ArbolBMas.abrir(RUTA_INDICE);
             ArchivoDatos datos = ArchivoDatos.abrir(RUTA_DATOS)) {

            arbol.reiniciarContadores();
            datos.reiniciarContadores();

            int[] mostrados = new int[1];

            int entregados = arbol.buscarRango(desde, hasta, (k, pos) -> {
                try {
                    if (mostrados[0] < limiteMostrar) {
                        String reg = datos.leer(pos);
                        System.out.println("[" + k + "] -> " + reg);
                        mostrados[0]++;
                    }
                } catch (IOException e) {
                    System.err.println("Error de lectura al recuperar registro en pos " + pos);
                }
            });

            System.out.println("-----------------------------------------------------------------");
            System.out.println("Resultados entregados: " + entregados + " (Mostrados: " + mostrados[0] + ")");
            System.out.println("Páginas leídas en índice: " + arbol.paginasLeidas());
        }
    }

    // 
    // COMANDO 4: secuencial <tt0111161 o 111161>
    // 
    private static void ejecutarSecuencial(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: java BMasApp secuencial <tt0111161 o 111161>");
            return;
        }

        long clave = parsearClave(args[1]);

        try (ArchivoDatos datos = ArchivoDatos.abrir(RUTA_DATOS)) {
            datos.reiniciarContadores();

            long pos = datos.busquedaSecuencial(clave);
            long regLeidos = datos.registrosLeidos();

            if (pos == -1L) {
                System.out.println("Búsqueda Secuencial: Clave no encontrada. Registros examinados: " + regLeidos);
            } else {
                String linea = datos.leer(pos);
                System.out.println("Posición: " + pos);
                System.out.println("Registro: " + linea);
                System.out.println("Registros leídos secuencialmente: " + regLeidos);
            }
        }
    }

    // 
    // COMANDO 5: info
    //
    private static void ejecutarInfo() throws IOException {
        try (ArbolBMas arbol = ArbolBMas.abrir(RUTA_INDICE);
             ArchivoDatos datos = ArchivoDatos.abrir(RUTA_DATOS)) {

            ArbolBMas.EstadisticasBMas est = arbol.estadisticas();

            System.out.println("=================================================================");
            System.out.println("                INFORMACIÓN DEL ÍNDICE Y DATOS                   ");
            System.out.println("=================================================================");
            System.out.println("Tamaño Página (P)    : " + arbol.tamPagina() + " bytes");
            System.out.println("Orden Interno (M)    : " + arbol.ordenInterno());
            System.out.println("Capacidad Hoja (L)   : " + arbol.capacidadHoja());
            System.out.println("Página Raíz          : #" + arbol.raiz());
            System.out.println("Niveles del Árbol    : " + arbol.niveles());
            System.out.println("Total de Páginas     : " + arbol.cantidadPaginas());
            System.out.println("Cantidad de Claves   : " + arbol.cantidadClaves());
            System.out.println("Hojas Totales        : " + est.hojas);
            System.out.println("Internos Totales     : " + est.internos);
            System.out.printf("Ocupación Hojas      : %.2f%%\n", est.ocupacionHojas * 100);
            System.out.printf("Ocupación Internos   : %.2f%%\n", est.ocupacionInternos * 100);
            System.out.println("Tamaño 'indice.dat'  : " + (new File(RUTA_INDICE).length()) + " bytes");
            System.out.println("Tamaño 'datos.dat'   : " + datos.tamano() + " bytes");
            System.out.println("=================================================================");
        }
    }

    // 
    // COMANDO 6: verificar
    // 
    private static void ejecutarVerificar() throws IOException {
        try (ArbolBMas arbol = ArbolBMas.abrir(RUTA_INDICE)) {
            String res = arbol.verificarInvariantes();
            System.out.println("Verificación de Invariantes: " + res);
        }
    }

    // 
    // COMANDO 7: pagina <nroPagina>
    // 
    private static void ejecutarPagina(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Uso: java BMasApp pagina <nroPagina>");
            return;
        }

        int nro = Integer.parseInt(args[1]);

        try (ArbolBMas arbol = ArbolBMas.abrir(RUTA_INDICE)) {
            if (nro < 0 || nro >= arbol.cantidadPaginas()) {
                System.out.println("Número de página fuera de rango [0, " + (arbol.cantidadPaginas() - 1) + "]");
                return;
            }
            System.out.println(arbol.volcarPagina(nro));
        }
    }

    // 
    // MÉTODOS AUXILIARES Y DE PARSEO
    // 

    /**
     * Convierte una cadena de texto tconst (ej. "tt0111161" o "111161") a valor numérico long.
     */
    private static long parsearClave(String tconstStr) {
        String limpia = tconstStr.trim();
        if (limpia.startsWith("tt")) {
            limpia = limpia.substring(2);
        }
        return Long.parseLong(limpia);
    }

    private static void imprimirUso() {
        System.out.println("Uso general de BMasApp:");
        System.out.println("  java BMasApp construir <title.basics.tsv> [filas=300000] [tamPagina=4096] [--aleatorio]");
        System.out.println("  java BMasApp buscar <tt0111161 o 111161>");
        System.out.println("  java BMasApp rango <desde> <hasta> [mostrar=10]");
        System.out.println("  java BMasApp secuencial <tt0111161 o 111161>");
        System.out.println("  java BMasApp info");
        System.out.println("  java BMasApp verificar");
        System.out.println("  java BMasApp pagina <nroPagina>");
    }
}