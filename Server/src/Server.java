// The server file for receiving sensor data.

import sun.misc.Signal;
import sun.misc.SignalHandler;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Date;


public class Server {
    static ServerSocket serverSocket = null;
    static FileWriter writer;

    public static void main(String[] args) throws IOException {
        Socket socket = null;

        if (args.length != 2) {
            System.out.println("Usage: java Sensor [PORT] [Filename]");
        }

        try {
            serverSocket = new ServerSocket(Integer.parseInt(args[0]));
        } catch (IOException ex) {
            System.out.println("Can't setup server on port number: " + args[0]);
        }
        writer = new FileWriter(args[1]);


        Signal.handle(new Signal("INT"), new SignalHandler() {
            @Override
            public void handle(Signal signal) {
                try {
                    serverSocket.close();
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                System.exit(0);
            }
        });

        while (true) {
            try {
                socket = serverSocket.accept();
            } catch (IOException ex) {
                System.out.println("Can't accept client connection:" + ex.getMessage());
            }
            // Start catching socket data.
            System.out.println("Now starting a new socket");
            System.out.flush();
            (new ClientThread(socket)).start();
        }

    }

    private static class ClientThread extends Thread {
        protected Socket socket;

        public ClientThread(Socket clientSocket) {
            socket = clientSocket;
        }

        public void run() {
            DataInputStream in = null;

            try {
                in = new DataInputStream(socket.getInputStream());
            } catch (IOException ex) {
                System.out.println("Can't get socket input stream. ");
            }

            // Creates buffer
            final int COUNT = 32;
            final int FLOAT = Float.SIZE / 8;
            final int LONG = Long.SIZE / 8;

            // The first INTEGER indicates the length of buffer size.
            final int oneBuffer = (LONG * 1 + FLOAT * 3 ) * COUNT;
//            byte[] bytes = new byte[oneBuffer];
            int count, sensor, type;

            try {
                type = in.readInt();
                sensor = in.readInt();
                count = in.readInt();
                System.out.printf("Type %d with %d data unit.\n", type, count);
                String ans = new String();
                for (int i = 0; i < count; i++) {
                    ans += String.format("%d,%d,%d,%f,%f,%f (%d)\n", type, sensor, in.readLong(),
                            in.readFloat(), in.readFloat(), in.readFloat(), (new Date()).getTime());
                }
                writer.write(ans);
//                System.out.printf("Type %d thread ends.\n", type);
                in.close();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

