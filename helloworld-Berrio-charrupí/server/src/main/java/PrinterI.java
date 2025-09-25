import Demo.Printer;
import Demo.Response;
import com.zeroc.Ice.Current;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.net.*;
import java.io.IOException;

public class PrinterI implements Printer
{
    public Response printString(String s, Current current)
    {
        long startTime = System.nanoTime();

        ParsedRequest req = parsePrefixedCommand(s);
        if (!req.valid) {
            return new Response(1, "Entrada inválida. Formato esperado usuario:host:comando");
        }
        String clientInfo = req.username + "@" + req.hostname;

        Response result;
        String cmd = req.command;
        if (cmd.startsWith("listifs")) {
            result = handleListIfs(clientInfo);
        } else if (isPositiveInteger(cmd)) {
            result = handleFibonacciAndPrimeFactors(cmd, clientInfo);
        } else if (cmd.startsWith("listports")) {
            result = handleListPorts(cmd, clientInfo);
        } else if (cmd.startsWith("!")) {
            result = handleSystemCommand(cmd, clientInfo);
        } else {
            result = new Response(1, "Comando no reconocido: " + cmd);
        }

        long endTime = System.nanoTime();
        long elapsedMs = (endTime - startTime) / 1_000_000L;
        result.responseTime = elapsedMs;
        return result;
    }

    private boolean isPositiveInteger(String str) {
        try {
            int value = Integer.parseInt(str);
            return value > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    private Response handleFibonacciAndPrimeFactors(String command, String clientInfo){
        int n = Integer.parseInt(command);
        System.out.println(clientInfo + " -> Fibonacci de n=" + n);
        String sequence = fibonacciIterative(n);
        List<Integer> primeFactors = primeFactorsOf(n);
        String msg = "Fibonacci(" + n + ") = " + sequence + "\n" +
                "Factores primos de " + n + ": " + primeFactors;
        return new Response(0, msg);
    }

    private String fibonacciIterative(int count) {
        if (count <= 0) return "";
        StringBuilder sb = new StringBuilder();
        long a = 0, b = 1;
        for (int i = 0; i < count; i++) {
            sb.append(a);
            if (i < count - 1) sb.append(' ');
            long next = a + b;
            a = b;
            b = next;
        }
        return sb.toString();
    }

    private List<Integer> primeFactorsOf(int value) {
        int n = value;
        List<Integer> factors = new ArrayList<>();
        if (n <= 1) return factors;
        while (n % 2 == 0) {
            factors.add(2);
            n /= 2;
        }
        int divisor = 3;
        while (divisor <= n / divisor) {
            while (n % divisor == 0) {
                factors.add(divisor);
                n /= divisor;
            }
            divisor += 2;
        }
        if (n > 1) factors.add(n);
        return factors;
    }

    private String listIfs(){
        try {
            StringBuilder output = new StringBuilder();
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()){
                NetworkInterface ni = nets.nextElement();
                output.append("[" + ni.getName() + "] " + ni.getDisplayName())
                        .append(" (" + (ni.isUp() ? "UP" : "DOWN") + ")\n");
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    output.append("  - ").append(addr.getHostAddress()).append("\n");
                }
            }
            return output.toString();
        } catch (SocketException e){
            return "Error al listar interfaces: " + e.getMessage();
        }
    }

    private Response handleListIfs(String clientInfo){
        String response = listIfs();
        System.out.println(clientInfo + " -> listifs");
        return new Response(0, response);
    }

    private Response handleListPorts(String command, String clientInfo) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return new Response(1, "Uso: listports <IPv4> [start end]");
        }

        String ip = parts[1].trim();
        int start = 1;
        int end = 1024;

        if (parts.length >= 4) {
            try {
                start = Integer.parseInt(parts[2]);
                end = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                return new Response(1, "start y end deben ser enteros");
            }
        }

        if (start < 1) start = 1;
        if (end > 65535) end = 65535;

        String result = scanPortsConcurrent(ip, start, end, 200, Math.min(64, Math.max(4, (end - start + 1) / 32)));
        System.out.println(clientInfo + " -> listports " + ip + " rango " + start + "-" + end);
        return new Response(0, result);
    }

    private String scanPortsConcurrent(String ip, int startPort, int endPort, int timeoutMs, int parallelism) {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(parallelism);
        java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();
        for (int port = startPort; port <= endPort; port++) {
            final int p = port;
            futures.add(pool.submit(() -> {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(ip, p), timeoutMs);
                    return p;
                } catch (IOException ex) {
                    return -1;
                }
            }));
        }
        pool.shutdown();
        StringBuilder sb = new StringBuilder();
        sb.append("Escaneo ").append(ip).append(" puertos ").append(startPort).append("-").append(endPort).append("\n");
        for (java.util.concurrent.Future<Integer> f : futures) {
            try {
                Integer open = f.get();
                if (open != null && open > 0) {
                    sb.append(open).append("/tcp open\n");
                }
            } catch (Exception ignored) { }
        }
        if (sb.toString().trim().isEmpty()) {
            sb.append("Sin puertos abiertos en el rango");
        }
        return sb.toString();
    }

    private Response handleSystemCommand(String message, String clientInfo) {
        String command = message.substring(1);
        System.out.println(clientInfo + " -> exec: " + command);

        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/sh", "-c", command);
            }
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            int code = process.waitFor();
            String result = "[exit=" + code + "]\n" + output;
            return new Response(0, result);
        } catch (Exception e) {
            return new Response(1, "Fallo ejecutando comando: " + e.getMessage());
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private ParsedRequest parsePrefixedCommand(String raw) {
        if (raw == null) return ParsedRequest.invalid();
        int first = raw.indexOf(':');
        int second = first < 0 ? -1 : raw.indexOf(':', first + 1);
        if (first < 0 || second < 0 || second + 1 >= raw.length()) {
            return ParsedRequest.invalid();
        }
        ParsedRequest pr = new ParsedRequest();
        pr.username = raw.substring(0, first);
        pr.hostname = raw.substring(first + 1, second);
        pr.command = raw.substring(second + 1);
        pr.valid = true;
        return pr;
    }

    private static class ParsedRequest {
        boolean valid;
        String username;
        String hostname;
        String command;

        static ParsedRequest invalid() {
            ParsedRequest p = new ParsedRequest();
            p.valid = false;
            return p;
        }
    }
}