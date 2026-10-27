import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;

public class ExperimentoBMas {

    private static final int SEED = 2026;
    private static final String TSV_DEFECTO = "title.basics.tsv";
    private static final int FILAS_DEFECTO = 300000;
    private static final int P_DEFECTO = 4096;

    public static void main(String[] args) {
        String comando = (args.length > 0) ? args[0].toLowerCase() : "todas";

        try {
            switch (comando) {
                case "sintetico":
                case "tablaa":
                    generarTablaA(1000000);
                    break;
                case "imdb":
                case "tablab":
                    generarTablaB(TSV_DEFECTO, FILAS_DEFECTO, P_DEFECTO);
                    break;
                case "busquedas":
                case "tablac":
                    generarTablaCyD(TSV_DEFECTO, FILAS_DEFECTO, P_DEFECTO, true, false);
                    break;
                case "rangos":
                case "tablad":
                    generarTablaCyD(TSV_DEFECTO, FILAS_DEFECTO, P_DEFECTO, false, true);
                    break;
                case "todas":
                default:
                    System.out.println(">>> EJECUTANDO SUITE COMPLETA DE EXPERIMENTOS (TABLAS A, B, C y D) <<<\n");
                    generarTablaA(1000000);
                    generarTablaB(TSV_DEFECTO, FILAS_DEFECTO, P_DEFECTO);
                    generarTablaCyD(TSV_DEFECTO, FILAS_DEFECTO, P_DEFECTO, true, true);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error durante la ejecución de los experimentos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // TABLA A: ESTRUCTURA (SINTÉTICA, N = 1.000.000)
    // =========================================================================
    public static void generarTablaA(int N) throws IOException {
        int[] tamañosP = {128, 256, 512, 1024, 2048, 4096};

        System.out.println("==================================================================================================================");
        System.out.println("                               TABLA A: ESTRUCTURA (SINTÉTICA, N = " + N + ")");
        System.out.println("==================================================================================================================");

        imprimirEncabezadoTablaA();

        long[] clavesCrecientes = new long[N];
        long[] clavesAleatorias = new long[N];
        for (int i = 0; i < N; i++) {
            clavesCrecientes[i] = i + 1;
            clavesAleatorias[i] = i + 1;
        }

        // Permutación Fisher-Yates con semilla 2026
        Random rng = new Random(SEED);
        for (int i = N - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            long temp = clavesAleatorias[i];
            clavesAleatorias[i] = clavesAleatorias[j];
            clavesAleatorias[j] = temp;
        }

        for (int P : tamañosP) {
            String rutaCrec = "exp_a_crec_" + P + ".dat";
            String rutaAlea = "exp_a_alea_" + P + ".dat";

            new File(rutaCrec).delete();
            new File(rutaAlea).delete();

            // Árbol Creciente
            ArbolBMas arbolCrec = ArbolBMas.crear(rutaCrec, P);
            for (int i = 0; i < N; i++) {
                arbolCrec.insertar(clavesCrecientes[i], clavesCrecientes[i]);
            }
            ArbolBMas.EstadisticasBMas estCrec = arbolCrec.estadisticas();
            int nivCrec = arbolCrec.niveles();
            int M = arbolCrec.ordenInterno();
            int L = arbolCrec.capacidadHoja();
            arbolCrec.close();

            // Árbol Aleatorio
            ArbolBMas arbolAlea = ArbolBMas.crear(rutaAlea, P);
            for (int i = 0; i < N; i++) {
                arbolAlea.insertar(clavesAleatorias[i], clavesAleatorias[i]);
            }
            ArbolBMas.EstadisticasBMas estAlea = arbolAlea.estadisticas();
            int nivAlea = arbolAlea.niveles();
            arbolAlea.close();

            new File(rutaCrec).delete();
            new File(rutaAlea).delete();

            System.out.printf("| %4d | %3d | %3d | %7d | %7d | %7.2f%% | %7.2f%% | %7d | %7d | %7.2f%% | %7.2f%% |\n",
                P, M, L,
                nivCrec, estCrec.hojas, estCrec.ocupacionHojas * 100.0, estCrec.ocupacionInternos * 100.0,
                nivAlea, estAlea.hojas, estAlea.ocupacionHojas * 100.0, estAlea.ocupacionInternos * 100.0
            );
        }
        System.out.println("==================================================================================================================\n");
    }

    // =========================================================================
    // TABLA B: CONSTRUCCIÓN CON IMDB
    // =========================================================================
    public static void generarTablaB(String rutaTsv, int maxFilas, int P) throws IOException {
        System.out.println("==================================================================================================================");
        System.out.println("                               TABLA B: CONSTRUCCIÓN CON IMDB (N = " + maxFilas + ", P = " + P + ")");
        System.out.println("==================================================================================================================");

        File fileTsv = new File(rutaTsv);
        if (!fileTsv.exists()) {
            System.err.println("Error: No se encuentra " + rutaTsv + ". Asegúrate de descomprimir title.basics.tsv.");
            return;
        }

        imprimirEncabezadoTablaB();
        medirConstruccionIMDb("En Orden", rutaTsv, maxFilas, P, false);
        medirConstruccionIMDb("Aleatorio", rutaTsv, maxFilas, P, true);
        System.out.println("==================================================================================================================\n");
    }

    private static void medirConstruccionIMDb(String modo, String rutaTsv, int maxFilas, int P, boolean aleatorio) throws IOException {
        String rutaDatos = "exp_b_datos.dat";
        String rutaIndice = "exp_b_indice.dat";

        new File(rutaDatos).delete();
        new File(rutaIndice).delete();

        long inicio = System.currentTimeMillis();

        ArchivoDatos datos = ArchivoDatos.crear(rutaDatos);
        ArbolBMas arbol = ArbolBMas.crear(rutaIndice, P);

        long[] claves = new long[maxFilas];
        long[] posiciones = new long[maxFilas];
        int cont = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(rutaTsv))) {
            String linea = br.readLine();
            if (linea != null && linea.startsWith("tconst")) {
                linea = br.readLine();
            }

            while (linea != null && cont < maxFilas) {
                if (!linea.trim().isEmpty()) {
                    String[] campos = linea.split("\t");
                    if (campos.length > 0) {
                        long clave = parsearClave(campos[0]);
                        long pos = datos.agregar(clave, linea);
                        claves[cont] = clave;
                        posiciones[cont] = pos;
                        cont++;
                    }
                }
                linea = br.readLine();
            }
        }

        if (aleatorio) {
            Random rng = new Random(SEED);
            for (int i = cont - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                long tmpC = claves[i]; claves[i] = claves[j]; claves[j] = tmpC;
                long tmpP = posiciones[i]; posiciones[i] = posiciones[j]; posiciones[j] = tmpP;
            }
        }

        arbol.reiniciarContadores();
        for (int i = 0; i < cont; i++) {
            arbol.insertar(claves[i], posiciones[i]);
        }

        long fin = System.currentTimeMillis();
        long tamDatosMB = datos.tamano();
        long tamIndiceMB = new File(rutaIndice).length();

        ArbolBMas.EstadisticasBMas est = arbol.estadisticas();
        int M = arbol.ordenInterno();
        int L = arbol.capacidadHoja();
        int niveles = arbol.niveles();
        long escrituras = arbol.paginasEscritas();

        datos.close();
        arbol.close();

        new File(rutaDatos).delete();
        new File(rutaIndice).delete();

        System.out.printf("| %-9s | %8d | %8.2f MB | %8.2f MB | %4d | %3d | %3d | %7d | %6d | %8d | %7.2f%% | %7.2f%% | %8d | %7d ms |\n",
            modo, cont, tamDatosMB / (1024.0 * 1024.0), tamIndiceMB / (1024.0 * 1024.0),
            P, M, L, niveles, est.hojas, est.internos,
            est.ocupacionHojas * 100.0, est.ocupacionInternos * 100.0, escrituras, (fin - inicio)
        );
    }

    // =========================================================================
    // TABLAS C Y D: BÚSQUEDAS EXACTAS Y POR RANGO SOBRE ÍNDICE DE IMDB
    // =========================================================================
    public static void generarTablaCyD(String rutaTsv, int maxFilas, int P, boolean hacerC, boolean hacerD) throws IOException {
        String rutaDatos = "exp_cd_datos.dat";
        String rutaIndice = "exp_cd_indice.dat";

        // 1. Construir el índice base en orden del archivo
        new File(rutaDatos).delete();
        new File(rutaIndice).delete();

        ArchivoDatos datos = ArchivoDatos.crear(rutaDatos);
        ArbolBMas arbol = ArbolBMas.crear(rutaIndice, P);

        try (BufferedReader br = new BufferedReader(new FileReader(rutaTsv))) {
            String linea = br.readLine();
            if (linea != null && linea.startsWith("tconst")) linea = br.readLine();

            int count = 0;
            while (linea != null && count < maxFilas) {
                if (!linea.trim().isEmpty()) {
                    String[] campos = linea.split("\t");
                    if (campos.length > 0) {
                        long c = parsearClave(campos[0]);
                        long pos = datos.agregar(c, linea);
                        arbol.insertar(c, pos);
                        count++;
                    }
                }
                linea = br.readLine();
            }
        }

        // 2. Extraer todas las claves usando buscarRango en un arreglo ordenado long[]
        int totalClaves = (int) arbol.cantidadClaves();
        long[] todasLasClaves = new long[totalClaves];
        int[] idx = new int[1];

        arbol.buscarRango(Long.MIN_VALUE, Long.MAX_VALUE, (k, pos) -> {
            todasLasClaves[idx[0]++] = k;
        });

        Random rng = new Random(SEED);

        // --- TABLA C: BÚSQUEDAS EXACTAS ---
        if (hacerC) {
            System.out.println("=========================================================================================");
            System.out.println("                               TABLA C: BÚSQUEDAS EXACTAS");
            System.out.println("=========================================================================================");
            imprimirEncabezadoTablaC();

            // A) 1000 claves existentes
            long[] exist1000 = new long[1000];
            for (int i = 0; i < 1000; i++) {
                exist1000[i] = todasLasClaves[rng.nextInt(totalClaves)];
            }

            // B) 1000 claves inexistentes
            long minKey = todasLasClaves[0];
            long maxKey = todasLasClaves[totalClaves - 1];
            long[] inexist1000 = new long[1000];
            int cInexist = 0;

            while (cInexist < 1000) {
                long cand = minKey + (long) (rng.nextDouble() * (maxKey - minKey + 1));
                if (busquedaBinaria(todasLasClaves, cand) == -1) {
                    inexist1000[cInexist++] = cand;
                }
            }

            // Medición 1: B+ Existentes (1000 consultas)
            long totalPagsIndiceExist = 0;
            long totalRegsExist = 0;
            long tStart = System.nanoTime();
            for (long k : exist1000) {
                arbol.reiniciarContadores();
                datos.reiniciarContadores();
                long pos = arbol.buscar(k);
                if (pos != -1L) datos.leer(pos);
                totalPagsIndiceExist += arbol.paginasLeidas();
                totalRegsExist += datos.registrosLeidos();
            }
            long tEnd = System.nanoTime();
            double usExist = (tEnd - tStart) / 1000.0 / 1000.0;

            System.out.printf("| %-22s | %9d | %22.2f | %22.2f | %15.2f |\n",
                "B+ Tree (Existentes)", 1000, (double) totalPagsIndiceExist / 1000.0, (double) totalRegsExist / 1000.0, usExist);

            // Medición 2: B+ Inexistentes (1000 consultas)
            long totalPagsIndiceInexist = 0;
            tStart = System.nanoTime();
            for (long k : inexist1000) {
                arbol.reiniciarContadores();
                arbol.buscar(k);
                totalPagsIndiceInexist += arbol.paginasLeidas();
            }
            tEnd = System.nanoTime();
            double usInexist = (tEnd - tStart) / 1000.0 / 1000.0;

            System.out.printf("| %-22s | %9d | %22.2f | %22.2f | %15.2f |\n",
                "B+ Tree (Inexistentes)", 1000, (double) totalPagsIndiceInexist / 1000.0, 0.0, usInexist);

            // Medición 3: Búsqueda Secuencial (Primeras 50 Existentes)
            long totalRegsSecExist = 0;
            tStart = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                datos.reiniciarContadores();
                datos.busquedaSecuencial(exist1000[i]);
                totalRegsSecExist += datos.registrosLeidos();
            }
            tEnd = System.nanoTime();
            double usSecExist = (tEnd - tStart) / 1000.0 / 50.0;

            System.out.printf("| %-22s | %9d | %22.2f | %22.2f | %15.2f |\n",
                "Secuencial (Existentes)", 50, 0.0, (double) totalRegsSecExist / 50.0, usSecExist);

            // Medición 4: Búsqueda Secuencial (Primeras 50 Inexistentes)
            long totalRegsSecInexist = 0;
            tStart = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                datos.reiniciarContadores();
                datos.busquedaSecuencial(inexist1000[i]);
                totalRegsSecInexist += datos.registrosLeidos();
            }
            tEnd = System.nanoTime();
            double usSecInexist = (tEnd - tStart) / 1000.0 / 50.0;

            System.out.printf("| %-22s | %9d | %22.2f | %22.2f | %15.2f |\n",
                "Secuencial (Inexistentes)", 50, 0.0, (double) totalRegsSecInexist / 50.0, usSecInexist);

            System.out.println("=========================================================================================\n");
        }

        // --- TABLA D: BÚSQUEDAS POR RANGO ---
        if (hacerD) {
            System.out.println("=========================================================================================");
            System.out.println("                               TABLA D: BÚSQUEDAS POR RANGO");
            System.out.println("=========================================================================================");
            imprimirEncabezadoTablaD();

            int[] anchosW = {10, 100, 1000, 10000};

            for (int w : anchosW) {
                long totalEntregados = 0;
                long totalPagsLeidas = 0;
                long totalHojasLeidas = 0;

                long tStart = System.nanoTime();
                for (int i = 0; i < 100; i++) {
                    long x = todasLasClaves[rng.nextInt(totalClaves)];
                    long hasta = x + w - 1;

                    arbol.reiniciarContadores();
                    int entregados = arbol.buscarRango(x, hasta, (k, pos) -> {});

                    long pagsLeidas = arbol.paginasLeidas();
                    long hojasLeidas = Math.max(0, pagsLeidas - (arbol.niveles() - 1));

                    totalEntregados += entregados;
                    totalPagsLeidas += pagsLeidas;
                    totalHojasLeidas += hojasLeidas;
                }
                long tEnd = System.nanoTime();
                double usPorConsulta = (tEnd - tStart) / 1000.0 / 100.0;

                System.out.printf("| %6d | %23.2f | %22.2f | %22.2f | %13.2f |\n",
                    w,
                    (double) totalEntregados / 100.0,
                    (double) totalPagsLeidas / 100.0,
                    (double) totalHojasLeidas / 100.0,
                    usPorConsulta
                );
            }
            System.out.println("=========================================================================================\n");
        }

        datos.close();
        arbol.close();

        new File(rutaDatos).delete();
        new File(rutaIndice).delete();
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================
    private static int busquedaBinaria(long[] arr, long val) {
        int ini = 0, fin = arr.length - 1;
        while (ini <= fin) {
            int med = (ini + fin) / 2;
            if (arr[med] == val) return med;
            if (arr[med] < val) ini = med + 1;
            else fin = med - 1;
        }
        return -1;
    }

    private static long parsearClave(String tconstStr) {
        String limpia = tconstStr.trim();
        if (limpia.startsWith("tt")) limpia = limpia.substring(2);
        return Long.parseLong(limpia);
    }

    private static void imprimirEncabezadoTablaA() {
        System.out.println("+------+-----+-----+-----------------------------------------+-----------------------------------------+");
        System.out.println("|      |     |     |          INSERCIÓN CRECIENTE            |           INSERCIÓN ALEATORIA           |");
        System.out.println("|  P   |  M  |  L  +---------+---------+-----------+---------+---------+---------+-----------+---------+");
        System.out.println("|      |     |     | Niveles |  Hojas  | Ocup.Hojo | Ocup.Int| Niveles |  Hojas  | Ocup.Hojo | Ocup.Int|");
        System.out.println("+------+-----+-----+---------+---------+-----------+---------+---------+---------+-----------+---------+");
    }

    private static void imprimirEncabezadoTablaB() {
        System.out.println("+-----------+----------+---------------+---------------+------+-----+-----+---------+--------+----------+-----------+-----------+----------+------------+");
        System.out.println("| Modo      | Registros| tam(datos)    | tam(indice)   |  P   |  M  |  L  | Niveles | Hojas  | Internos | Ocup.Hojo | Ocup.Int  | Págs.Escr| Tiempo(ms) |");
        System.out.println("+-----------+----------+---------------+---------------+------+-----+-----+---------+--------+----------+-----------+-----------+----------+------------+");
    }

    private static void imprimirEncabezadoTablaC() {
        System.out.println("+------------------------+-----------+------------------------+------------------------+-----------------+");
        System.out.println("| Método                 | Consultas | Págs. Índice / Consult | Regs. Leídos / Consult | µs / Consulta   |");
        System.out.println("+------------------------+-----------+------------------------+------------------------+-----------------+");
    }

    private static void imprimirEncabezadoTablaD() {
        System.out.println("+--------+-------------------------+------------------------+------------------------+---------------+");
        System.out.println("| Ancho w| Regs.Entreg. / Consulta | Págs. Leídas / Consult | Hojas Leídas / Consult | µs / Consulta |");
        System.out.println("+--------+-------------------------+------------------------+------------------------+---------------+");
    }
}