import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Iperfer {
    public static void main(String[] args) throws IOException {
        if (args.length == 7 && args[0].equals("-c") && args[1].equals("-h") && args[3].equals("-p")
                && args[5].equals("-t")) {
            // Case 1: java Iperfer -c -h <server hostname> -p <server port> -t <time>
            int port = parsePort(args[4]);
            int time = 0;
            try {
                time = Integer.parseInt(args[6]);
            } catch (Exception e) {
                printInvalidArguments();
            }
            Client.run(args[2], port, time);
        } else if (args.length == 3 && args[0].equals("-s") && args[1].equals("-p")) {
            // Case 2: java Iperfer -s -p <listen port>
            int port = parsePort(args[2]);
            Server.run(port);
        } else {
            printInvalidArguments();
        }
    }

    private static int parsePort(String portString) {
        int port = 0;
        try {
            port = Integer.parseInt(portString);
            if (port < 1024 || port > 65535) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            System.out.println("Error: port number must be in the range 1024 to 65535");
            System.exit(1);
        }

        return port;
    }

    private static void printInvalidArguments() {
        System.out.println("Error: invalid arguments");
        System.exit(1);
    }
}

class Client {
    static void run(String host, int portNumber, int time) throws IOException {
        long startTime = System.currentTimeMillis();

        Socket clientSoc = new Socket(host, portNumber);
        OutputStream outStream = clientSoc.getOutputStream();

        long sentByte = 0;
        byte[] data = new byte[1000];

        while (System.currentTimeMillis() - startTime <= time * 1000) {
            outStream.write(data);
            sentByte += data.length;
        }

        System.out.printf("sent=%d KB\trate=%.3f Mbps\n", (int) (sentByte / 1000), sentByte / 1000.0 / 1000 * 8 / time);

        clientSoc.close();
    }
}

class Server {
    static void run(int serverPort) throws IOException {
        ServerSocket serverSoc = new ServerSocket(serverPort);
        Socket clientSoc = serverSoc.accept();
        long startTime = System.currentTimeMillis();

        DataInputStream inStream = new DataInputStream(clientSoc.getInputStream());

        byte[] data = new byte[1000];

        long getByte = 0;
        long readBytes;
        while ((readBytes = inStream.read(data)) != -1) {
            getByte += readBytes;
        }

        long endTime = System.currentTimeMillis();
        long time = endTime - startTime;

        System.out.printf("received=%d KB\trate=%.3f Mbps\n", (int) (getByte / 1000), getByte / 1000.0 * 8 / time);

        clientSoc.close();
        serverSoc.close();
    }
}