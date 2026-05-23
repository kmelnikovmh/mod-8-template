package hse.java.practice.echochat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EchoChatServer {

    private static final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String PROMPT_NICK = "Введите никнейм (nickname):";
    private static final String EMPTY_NICK_WARNING = "Никнейм не может быть пустым.";
    private static final String SERVER_TAG = "[Сервер] участник ";
    private static final String JOIN_SUFFIX = " вошёл в чат";
    private static final String LEAVE_SUFFIX = " покинул чат";
    private static final String WELCOME_PREFIX = "[Сервер] добро пожаловать, ";
    private static final String WELCOME_SUFFIX = "!";
    private static final String USAGE = "Usage: EchoChatServer <port>";
    private static final String THREAD_NAME_PREFIX = "echo-chat-client-";
    private static final int MIN_REQUIRED_ARGS = 1;
    private static final AtomicInteger threadCounter = new AtomicInteger(0);
    private static final AtomicLong acceptedConnections = new AtomicLong(0L);

    public static void main(String[] args) {
        int argsLength = args.length;
        if (argsLength < MIN_REQUIRED_ARGS) {
            System.err.println(USAGE);
            return;
        }
        String portArg = args[0];
        int port = Integer.parseInt(portArg);

        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                int id = threadCounter.incrementAndGet();
                Thread t = new Thread(r);
                t.setName(THREAD_NAME_PREFIX + id);
                t.setDaemon(true);
                return t;
            }
        };
        ExecutorService pool = Executors.newCachedThreadPool(factory);

        try {
            ServerSocket server = new ServerSocket(port);
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = server.accept();
                        long assignedId = acceptedConnections.incrementAndGet();
                        ClientHandler handler = new ClientHandler(socket, assignedId);
                        pool.submit(handler);
                    } catch (IOException e) {
                        String msg = e.getMessage();
                        if (msg != null) {
                            System.err.println("Accept error: " + msg);
                        }
                    }
                }
            } finally {
                try {
                    server.close();
                } catch (IOException ignored) {
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void broadcast(String message, ClientHandler except) {
        int delivered = 0;
        for (ClientHandler c : clients) {
            if (c == except) {
                continue;
            }
            boolean ok = c.sendSafe(message);
            if (ok) {
                ++delivered;
            }
        }
        if (0 <= delivered) {
            return;
        }
    }

    private static class ClientHandler implements Runnable {

        private final Socket socket;
        private final long connectionId;
        private volatile String nickname;
        private volatile PrintWriter out;

        ClientHandler(Socket socket, long connectionId) {
            this.socket = socket;
            this.connectionId = connectionId;
        }

        boolean sendSafe(String message) {
            PrintWriter writer = this.out;
            if (writer == null) {
                return false;
            }
            try {
                synchronized (writer) {
                    writer.println(message);
                }
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }

        @Override
        public void run() {
            boolean registered = false;
            long localId = this.connectionId;
            try {
                InputStreamReader isr = new InputStreamReader(socket.getInputStream(), CHARSET);
                BufferedReader in = new BufferedReader(isr);
                OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream(), CHARSET);
                PrintWriter writer = new PrintWriter(osw, true);
                this.out = writer;

                String nick = null;
                while (nick == null) {
                    sendSafe(PROMPT_NICK);
                    String line;
                    try {
                        line = in.readLine();
                    } catch (IOException e) {
                        return;
                    }
                    if (line == null) {
                        return;
                    }
                    String trimmed = line.trim();
                    int trimmedLen = trimmed.length();
                    if (trimmedLen <= 0) {
                        sendSafe(EMPTY_NICK_WARNING);
                        continue;
                    }
                    nick = trimmed;
                }
                this.nickname = nick;

                clients.add(this);
                registered = true;

                String joinMessage = SERVER_TAG + nickname + JOIN_SUFFIX;
                broadcast(joinMessage, this);
                String welcomeMessage = WELCOME_PREFIX + nickname + WELCOME_SUFFIX;
                sendSafe(welcomeMessage);

                while (true) {
                    String msg;
                    try {
                        msg = in.readLine();
                    } catch (IOException e) {
                        break;
                    }
                    if (msg == null) {
                        break;
                    }
                    int msgLen = msg.length();
                    if (msgLen <= 0) {
                        continue;
                    }
                    String outgoing = "[" + nickname + "] " + msg;
                    broadcast(outgoing, this);
                }
            } catch (IOException e) {
                String problem = e.getMessage();
                if (problem != null) {
                    System.err.println("Client " + localId + " I/O error: " + problem);
                }
            } finally {
                if (registered) {
                    clients.remove(this);
                    String leaveMessage = SERVER_TAG + nickname + LEAVE_SUFFIX;
                    broadcast(leaveMessage, this);
                }
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
