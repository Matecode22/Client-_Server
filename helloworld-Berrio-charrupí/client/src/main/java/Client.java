import Demo.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Client {
    public static void main(String[] args) {
        java.util.List<String> extraArgs = new java.util.ArrayList<>();

        try (com.zeroc.Ice.Communicator communicator = com.zeroc.Ice.Util.initialize(args, "config.client", extraArgs);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            Demo.PrinterPrx service = Demo.PrinterPrx
                    .checkedCast(communicator.propertyToProxy("Printer.Proxy"));

            if (service == null) {
                throw new Error("Invalid proxy");
            }

            String username = System.getenv("USERNAME");
            if (username == null || username.isEmpty()) {
                username = System.getProperty("user.name", "unknown");
            }
            String hostname;
            try {
                hostname = java.net.InetAddress.getLocalHost().getCanonicalHostName();
            } catch (Exception ex) {
                hostname = "unknown-host";
            }
            final String prefix = username + ":" + hostname + ":";
            System.out.println("Cliente listo como " + prefix);

            while (true) {
                mostrarMenu();
                System.out.print("Seleccione una opción: ");
                String opcion = console.readLine();
                if (opcion == null) break; // EOF
                opcion = opcion.trim();

                String comando;
                switch (opcion) {
                    case "1":
                        System.out.print("Ingrese n (entero positivo) para Fibonacci: ");
                        comando = leerLinea(console);
                        if (comando == null) return;
                        comando = comando.trim();
                        if (comando.isEmpty()) continue;
                        break;
                    case "2":
                        comando = "listifs";
                        break;
                    case "3":
                        System.out.print("IPv4 a escanear: ");
                        String ip = leerLinea(console);
                        if (ip == null) return;
                        ip = ip.trim();
                        if (ip.isEmpty()) continue;
                        System.out.print("Rango opcional inicio fin (ej. 1 1024) o Enter: ");
                        String rango = leerLinea(console);
                        if (rango == null || rango.trim().isEmpty()) {
                            comando = "listports " + ip;
                        } else {
                            comando = "listports " + ip + " " + rango.trim();
                        }
                        break;
                    case "4":
                        System.out.print("Comando del sistema (sin el !): ");
                        String cmd = leerLinea(console);
                        if (cmd == null) return;
                        cmd = cmd.trim();
                        if (cmd.isEmpty()) continue;
                        comando = "!" + cmd;
                        break;
                    case "5":
                    case "exit":
                        return;
                    default:
                        System.out.println("Opción no válida. También puede escribir un comando directo o '5' para salir.");
                        continue;
                }

                Response response = service.printString(prefix + comando);
                System.out.println("Servidor -> " + response.value);
                System.out.println("latencia: " + response.responseTime + " ms\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void mostrarMenu() {
        System.out.println();
        System.out.println("===== Menú =====");
        System.out.println("1) Fibonacci (enviar n)");
        System.out.println("2) Interfaces (listifs)");
        System.out.println("3) Puertos (listports <ip> [start end])");
        System.out.println("4) Ejecutar comando (!<cmd>)");
        System.out.println("5) Salir");
    }

    private static String leerLinea(BufferedReader console) throws java.io.IOException {
        return console.readLine();
    }
}