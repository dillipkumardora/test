import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class BrokerServer1 extends WebSocketServer {
    private static WebSocket hostSocket = null;
    private static final Map<String, WebSocket> viewerSockets = new ConcurrentHashMap<>();
    private static final int MAX_FRAME_SIZE = 64 * 1024; // 64KB chunks

    public BrokerServer1(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientType = handshake.getFieldValue("clientType");
        if ("host".equals(clientType)) {
            if (hostSocket == null) {
                hostSocket = conn;
                System.out.println("Host connected: " + conn.getRemoteSocketAddress());
            } else {
                conn.close(1000, "Only one Host can connect at a time.");
            }
        } else if ("viewer".equals(clientType)) {
            viewerSockets.put(conn.getRemoteSocketAddress().toString(), conn);
            System.out.println("Viewer connected: " + conn.getRemoteSocketAddress());
            // Notify host that a viewer connected
            if (hostSocket != null && hostSocket.isOpen()) {
                hostSocket.send("viewer_connected");
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        if (conn == hostSocket) {
            hostSocket = null;
            System.out.println("Host disconnected");
        } else {
            viewerSockets.remove(conn.getRemoteSocketAddress().toString());
            System.out.println("Viewer disconnected: " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        if (conn == hostSocket) {
            // Forward image data to all viewers
            byte[] imageData = new byte[message.remaining()];
            message.get(imageData);

            // Calculate checksum
            String checksum = calculateChecksum(imageData);

            // Send size of image data and checksum before chunking
            String sizeMessage = "size:" + imageData.length + ":" + checksum;
            for (WebSocket viewer : viewerSockets.values()) {
                if (viewer.isOpen()) {
                    viewer.send(sizeMessage);
                }
            }

            // Split into smaller chunks if necessary
            int offset = 0;
            while (offset < imageData.length) {
                int chunkSize = Math.min(MAX_FRAME_SIZE, imageData.length - offset);
                ByteBuffer chunk = ByteBuffer.wrap(imageData, offset, chunkSize);

                for (WebSocket viewer : viewerSockets.values()) {
                    if (viewer.isOpen()) {
                        viewer.send(chunk.duplicate());
                    }
                }
                offset += chunkSize;
            }

            // Notify viewers that transmission is complete
            for (WebSocket viewer : viewerSockets.values()) {
                if (viewer.isOpen()) {
                    viewer.send("end");
                }
            }

            System.out.println("Forwarded image data: " + imageData.length + " bytes to " + 
                                 viewerSockets.size() + " viewers");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (viewerSockets.containsValue(conn)) {
            // Forward control messages from viewer to host
            if (hostSocket != null && hostSocket.isOpen()) {
                hostSocket.send(message);
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("Error on connection " + 
            (conn != null ? conn.getRemoteSocketAddress() : "null") + 
            ": " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Broker Server started Successfully");
    }

    public static void main(String[] args) {
        int port = 5000;
        BrokerServer1 server = new BrokerServer1(port);
        server.start();
        System.out.println("Broker Server started on port: " + port);
    }

    private static String calculateChecksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }
}
