// g28 - Jorge Hermosilla 5727706 
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ArchivoDatos implements AutoCloseable {

    private RandomAccessFile archivo;
    private String rutaArchivo;
    private long registrosLeidos;

    private ArchivoDatos(RandomAccessFile archivo, String rutaArchivo) {
        this.archivo = archivo;
        this.rutaArchivo = rutaArchivo;
        this.registrosLeidos = 0;
    }

    /**
     * Crea un archivo de datos nuevo (si ya existía, lo reemplaza).
     */
    public static ArchivoDatos crear(String ruta) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(ruta, "rw");
        raf.setLength(0); // Vacía el archivo si existía
        return new ArchivoDatos(raf, ruta);
    }

    /**
     * Abre un archivo de datos existente.
     */
    public static ArchivoDatos abrir(String ruta) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(ruta, "rw");
        return new ArchivoDatos(raf, ruta);
    }

    /**
     * Agrega un registro al final de datos.dat.
     * Formato: long clave (8 bytes) | int largo (4 bytes) | bytes en UTF-8 (largo bytes)
     *
     * @param clave Clave numérica del registro (ej. 111161)
     * @param linea Línea completa del TSV
     * @return La posición física (byte offset) donde comienza el registro.
     */
    public long agregar(long clave, String linea) throws IOException {
        long pos = archivo.length(); // Posición de inicio al final del archivo
        archivo.seek(pos);

        byte[] bytesLinea = linea.getBytes(StandardCharsets.UTF_8);//convierte la linea en un bytes[] para poder insertar en el archivo 

        archivo.writeLong(clave);
        archivo.writeInt(bytesLinea.length);
        archivo.write(bytesLinea);

        return pos;
    }

    /**
     * Lee el registro almacenado en la posición 'pos' dada.
     *
     * @param pos Desplazamiento en bytes del registro.
     * @return La línea de texto del registro.
     */
    public String leer(long pos) throws IOException {
        archivo.seek(pos);

        long claveLeida = archivo.readLong();
        int largo = archivo.readInt();

        byte[] buffer = new byte[largo];
        archivo.readFully(buffer);

        this.registrosLeidos++; // Incrementa contador de lecturas aleatorias

        return new String(buffer, StandardCharsets.UTF_8);
    }

    /**
     * Recorre el archivo secuencialmente desde el inicio buscando un registro por clave.
     *
     * @param claveBuscada Clave a encontrar.
     * @return La posición física del registro o -1L si no existe.
     */
    public long busquedaSecuencial(long claveBuscada) throws IOException {
        archivo.seek(0);
        long tam = archivo.length();

        while (archivo.getFilePointer() < tam) {
            long posActual = archivo.getFilePointer();

            long clave = archivo.readLong();
            int largo = archivo.readInt();

            this.registrosLeidos++; // Cuenta cada registro examinado secuencialmente

            if (clave == claveBuscada) {
                return posActual; // Encontrado
            }

            // Saltarse los bytes del contenido para avanzar al siguiente registro
            archivo.skipBytes(largo);
        }

        return -1L; // No encontrado
    }

    public long registrosLeidos() {
        return this.registrosLeidos;
    }

    public void reiniciarContadores() {
        this.registrosLeidos = 0;
    }

    public long tamano() throws IOException {
        return archivo.length();
    }

    @Override
    public void close() throws IOException {
        if (archivo != null) {
            archivo.close();
        }
    }
}