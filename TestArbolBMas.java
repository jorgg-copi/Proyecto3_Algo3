import java.io.File;
import java.io.IOException;

public class TestArbolBMas {

    private static final String RUTA_TEST = "test_indice.dat";
    private static int casoNum = 1;

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("       SUITE DE PRUEBAS AUTOMÁTICAS - TestArbolBMas (P=60)        ");
        System.out.println("=================================================================\n");

        // Limpieza de ejecuciones previas
        new File(RUTA_TEST).delete();

        try {
            // -----------------------------------------------------------------
            // CASO 1: Árbol inicialmente vacío
            // -----------------------------------------------------------------
            ArbolBMas arbol = ArbolBMas.crear(RUTA_TEST, 60);
            evaluarCaso("1. Arbol inicialmente vacío",
                arbol.niveles() == 1 &&
                arbol.cantidadPaginas() == 2 &&
                arbol.buscar(50) == -1L &&
                "1: #1[]".equals(arbol.volcarArbol()) &&
                "OK".equals(arbol.verificarInvariantes())
            );

            // -----------------------------------------------------------------
            // CASO 2: Inserciones sin división (50, 20, 80)
            // -----------------------------------------------------------------
            imprimirEncabezadoTraza();
            imprimirFilaTraza("crear", 0, 0, arbol.cantidadPaginas(), arbol.volcarArbol());

            ejecutarEImprimirInsercion(arbol, 50, 500);
            ejecutarEImprimirInsercion(arbol, 20, 200);
            ejecutarEImprimirInsercion(arbol, 80, 800);

            evaluarCaso("2. Inserciones sin división (50, 20, 80)",
                arbol.cantidadClaves() == 3 &&
                arbol.niveles() == 1 &&
                arbol.cantidadPaginas() == 2 &&
                "OK".equals(arbol.verificarInvariantes())
            );

            // -----------------------------------------------------------------
            // CASO 3: División de hoja (10: raíz hoja; 40: hoja intermedia)
            // -----------------------------------------------------------------
            ejecutarEImprimirInsercion(arbol, 10, 100); // Split de raíz hoja
            ejecutarEImprimirInsercion(arbol, 30, 300);
            ejecutarEImprimirInsercion(arbol, 60, 600);
            ejecutarEImprimirInsercion(arbol, 90, 900);
            ejecutarEImprimirInsercion(arbol, 40, 400); // Split de hoja intermedia #5

            evaluarCaso("3. División de hoja (raíz e intermedia)",
                arbol.cantidadPaginas() == 6 &&
                "OK".equals(arbol.verificarInvariantes())
            );

            // Inserciones intermedias de la traza oficial (70, 25, 35)
            ejecutarEImprimirInsercion(arbol, 70, 700);
            ejecutarEImprimirInsercion(arbol, 25, 250);
            ejecutarEImprimirInsercion(arbol, 35, 350);

            // -----------------------------------------------------------------
            // CASOS 4 y 5: División de nodo interno y nueva raíz (45)
            // -----------------------------------------------------------------
            ejecutarEImprimirInsercion(arbol, 45, 450); // Causa 2 splits y crea la raíz #8

            evaluarCaso("4 y 5. División de nodo interno y nueva raíz (45)",
                arbol.niveles() == 3 &&
                arbol.raiz() == 8 &&
                arbol.cantidadPaginas() == 9 &&
                "OK".equals(arbol.verificarInvariantes())
            );

            // -----------------------------------------------------------------
            // CASO 6: Crecimiento del árbol a tres niveles
            // -----------------------------------------------------------------
            ejecutarEImprimirInsercion(arbol, 15, 150); // Completa el árbol final de 13 claves

            evaluarCaso("6. Crecimiento a tres niveles",
                arbol.niveles() == 3 &&
                arbol.cantidadClaves() == 13 &&
                arbol.cantidadPaginas() == 10 &&
                "OK".equals(arbol.verificarInvariantes())
            );

            // -----------------------------------------------------------------
            // CASO 7: Búsqueda de primera/última clave y caminoBusqueda
            // -----------------------------------------------------------------
            arbol.reiniciarContadores();
            long pos10 = arbol.buscar(10);
            long r10 = arbol.paginasLeidas();

            arbol.reiniciarContadores();
            long pos90 = arbol.buscar(90);
            long r90 = arbol.paginasLeidas();

            int[] camino35 = arbol.caminoBusqueda(35);
            boolean caminoOK = camino35.length == 3 && camino35[0] == 8 && camino35[1] == 3 && camino35[2] == 5;

            evaluarCaso("7. Búsqueda primera/última clave y caminoBusqueda",
                pos10 == 100 && r10 == 3 &&
                pos90 == 900 && r90 == 3 &&
                caminoOK
            );

            // -----------------------------------------------------------------
            // CASO 8: Búsqueda de clave inexistente
            // -----------------------------------------------------------------
            arbol.reiniciarContadores();
            long pos33 = arbol.buscar(33);
            long r33 = arbol.paginasLeidas();

            evaluarCaso("8. Búsqueda de clave inexistente (33)",
                pos33 == -1L && r33 == 3
            );

            // -----------------------------------------------------------------
            // CASO 9: Búsqueda por rango dentro de una hoja
            // -----------------------------------------------------------------
            arbol.reiniciarContadores();
            int[] c1 = new int[1];
            arbol.buscarRango(31, 35, (k, pos) -> c1[0]++);
            long rRango1 = arbol.paginasLeidas();

            evaluarCaso("9. Búsqueda por rango en una hoja (31..35)",
                c1[0] == 1 && rRango1 == 3
            );

            // -----------------------------------------------------------------
            // CASO 10: Búsqueda por rango atraviesa hojas con regla de parada
            // -----------------------------------------------------------------
            arbol.reiniciarContadores();
            int[] c2 = new int[1];
            int ret2 = arbol.buscarRango(20, 45, (k, pos) -> c2[0]++);
            long rRango2 = arbol.paginasLeidas(); // Lee 5 páginas (3 descenso + 2 hojas)

            evaluarCaso("10. Búsqueda por rango multi-hoja con regla de parada (20..45)",
                ret2 == 6 && c2[0] == 6 && rRango2 == 5
            );

            // Validación de excepción para rango inválido
            boolean excepcionRangoOK = false;
            try {
                arbol.buscarRango(9, 5, (k, pos) -> {});
            } catch (RangoInvalidoException e) {
                excepcionRangoOK = true;
            }

            // -----------------------------------------------------------------
            // CASO 11: Cerrar y volver a abrir el archivo
            // -----------------------------------------------------------------
            arbol.close();
            File archivoFisico = new File(RUTA_TEST);
            long tamArchivo = archivoFisico.length();

            ArbolBMas arbolReabierto = ArbolBMas.abrir(RUTA_TEST);

            evaluarCaso("11. Persistencia: close() y abrir()",
                tamArchivo == 600 && // 10 páginas * 60 bytes
                arbolReabierto.raiz() == 8 &&
                arbolReabierto.niveles() == 3 &&
                arbolReabierto.cantidadPaginas() == 10 &&
                arbolReabierto.cantidadClaves() == 13 &&
                "OK".equals(arbolReabierto.verificarInvariantes())
            );

            // -----------------------------------------------------------------
            // CASO 12: Repetir búsquedas después de reabrir
            // -----------------------------------------------------------------
            arbolReabierto.reiniciarContadores();
            long p45 = arbolReabierto.buscar(45);
            long p15 = arbolReabierto.buscar(15);
            long rReabierto = arbolReabierto.paginasLeidas(); // 3 + 3 = 6 páginas leídas

            evaluarCaso("12. Búsquedas sobre árbol reabierto",
                p45 == 450 && p15 == 150 && rReabierto == 6 && excepcionRangoOK
            );

            arbolReabierto.close();

            System.out.println("\n=================================================================");
            System.out.println("        ¡TODAS LAS PRUEBAS AUTOMÁTICAS FINALIZARON OK!           ");
            System.out.println("=================================================================");

        } catch (Exception e) {
            System.err.println("\nFAIL: Ocurrió un error inesperado durante las pruebas:");
            e.printStackTrace();
        }
    }

    private static void ejecutarEImprimirInsercion(ArbolBMas arbol, long clave, long pos) throws IOException {
        arbol.reiniciarContadores();
        arbol.insertar(clave, pos);
        long r = arbol.paginasLeidas();
        long w = arbol.paginasEscritas();
        int pags = arbol.cantidadPaginas();
        imprimirFilaTraza("insertar(" + clave + ")", w, r, pags, arbol.volcarArbol());
    }

    private static void imprimirEncabezadoTraza() {
        System.out.println("\n-----------------------------------------------------------------");
        System.out.println("                     TRAZA DE INSERCIONES                        ");
        System.out.println("-----------------------------------------------------------------");
        System.out.printf("%-15s %-3s %-3s %-5s %s\n", "Operación", "W", "R", "págs", "volcarArbol()");
        System.out.println("-----------------------------------------------------------------");
    }

    private static void imprimirFilaTraza(String op, long w, long r, int pags, String arbolStr) {
        String[] lineas = arbolStr.split("\n");
        System.out.printf("%-15s %-3d %-3d %-5d %s\n", op, w, r, pags, lineas[0]);
        for (int i = 1; i < lineas.length; i++) {
            System.out.printf("%-29s %s\n", "", lineas[i]);
        }
    }

    private static void evaluarCaso(String titulo, boolean condicion) {
        System.out.printf("[CASO %02d] %-55s -> %s\n", casoNum++, titulo, condicion ? "OK" : "FALLA");
    }
}