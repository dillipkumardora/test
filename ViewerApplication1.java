////import javax.swing.*;
////import java.awt.*;
////import java.awt.event.*;
////import java.awt.image.BufferedImage;
////import java.io.ByteArrayInputStream;
////import java.io.ByteArrayOutputStream;
////import java.net.URI;
////import java.nio.ByteBuffer;
////import java.util.concurrent.ConcurrentLinkedQueue;
////import javax.imageio.ImageIO;
////import org.java_websocket.client.WebSocketClient;
////import org.java_websocket.handshake.ServerHandshake;
////
////public class ViewerApplication1 {
////    private static final String BROKER_SERVER_URL = "ws://129.154.243.213:5000";
////    private static WebSocketClient client;
////    private static JFrame frame;
////    private static BufferedImage screenImage;
////    private static final ConcurrentLinkedQueue<byte[]> imageChunks = new ConcurrentLinkedQueue<>();
////    private static volatile boolean receivingImage = false;
////    private static volatile int expectedImageSize = 0;
////    private static volatile int receivedImageSize = 0;
////
////    public static void main(String[] args) {
////        try {
////            initializeWebSocketClient();
////            initializeViewerFrame();
////        } catch (Exception e) {
////            e.printStackTrace();
////        }
////    }
////
////    private static void initializeWebSocketClient() throws Exception {
////        client = new WebSocketClient(new URI(BROKER_SERVER_URL)) {
////            @Override
////            public void onOpen(ServerHandshake handshake) {
////                System.out.println("Connected to broker as Viewer.");
////                client.send("viewer_connected");
////            }
////
////            @Override
////            public void onMessage(String message) {
////                if (message.startsWith("size:")) {
////                    expectedImageSize = Integer.parseInt(message.substring(5));
////                    imageChunks.clear();
////                    receivedImageSize = 0;
////                    receivingImage = true;
////                    System.out.println("Started receiving image of size: " + expectedImageSize);
////                } else if (message.equals("end")) {
////                    receivingImage = false;
////                    System.out.println("Finished receiving image, processing...");
////                    processReceivedImage();
////                }
////            }
////
////            @Override
////            public void onMessage(ByteBuffer message) {
////                if (receivingImage) {
////                    byte[] chunk = new byte[message.remaining()];
////                    message.get(chunk);
////                    imageChunks.add(chunk);
////                    receivedImageSize += chunk.length;
////                    System.out.println("Received binary chunk of size: " + chunk.length + ", total received: " + receivedImageSize);
////                }
////            }
////
////            @Override
////            public void onClose(int code, String reason, boolean remote) {
////                System.out.println("Disconnected from broker: " + reason);
////                receivingImage = false;
////            }
////
////            @Override
////            public void onError(Exception ex) {
////                System.err.println("WebSocket error: " + ex.getMessage());
////                ex.printStackTrace();
////            }
////        };
////
////        client.addHeader("clientType", "viewer");
////        client.connect();
////
////        while (!client.isOpen()) {
////            Thread.sleep(100);
////        }
////    }
////
////    private static void initializeViewerFrame() {
////        frame = new JFrame("Viewer Application");
////        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
////        frame.setSize(800, 600);
////        frame.setLocationRelativeTo(null);
////
////        JPanel panel = new JPanel() {
////            @Override
////            protected void paintComponent(Graphics g) {
////                super.paintComponent(g);
////                if (screenImage != null) {
////                    int width = getWidth();
////                    int height = getHeight();
////                    g.drawImage(screenImage, 0, 0, width, height, null);
////                }
////            }
////        };
////
////        panel.addMouseMotionListener(new MouseMotionAdapter() {
////            @Override
////            public void mouseMoved(MouseEvent e) {
////                sendMouseMoveCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
////            }
////        });
////
////        panel.addMouseListener(new MouseAdapter() {
////            @Override
////            public void mouseClicked(MouseEvent e) {
////                if (e.getClickCount() == 2) {
////                    sendMouseDoubleClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
////                } else if (e.getButton() == MouseEvent.BUTTON1) {
////                    sendMouseClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
////                } else if (e.getButton() == MouseEvent.BUTTON3) { // Right-click
////                    sendRightClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
////                }
////            }
////        });
////
////        panel.addMouseWheelListener(e -> sendMouseScrollCommand(e.getWheelRotation()));
////
////        frame.addKeyListener(new KeyAdapter() {
////            @Override
////            public void keyPressed(KeyEvent e) {
////                sendKeyPressCommand(e.getKeyCode());
////            }
////
////            @Override
////            public void keyReleased(KeyEvent e) {
////                sendKeyReleaseCommand(e.getKeyCode());
////            }
////        });
////
////        frame.add(panel);
////        frame.setVisible(true);
////    }
////
////    private static void processReceivedImage() {
////        try {
////            ByteArrayOutputStream baos = new ByteArrayOutputStream();
////            for (byte[] chunk : imageChunks) {
////                baos.write(chunk);
////            }
////            byte[] imageData = baos.toByteArray();
////            
////            // Debug statement
////            System.out.println("Assembled image size: " + imageData.length);
////
////            if (imageData.length == expectedImageSize) {
////                screenImage = ImageIO.read(new ByteArrayInputStream(imageData));
////                frame.repaint();
////                System.out.println("Image displayed successfully.");
////            } else {
////                System.err.println("Image size mismatch: expected " + expectedImageSize + ", but got " + imageData.length);
////            }
////        } catch (Exception e) {
////            System.err.println("Error processing received image: " + e.getMessage());
////        }
////    }
////
////    private static void sendMouseMoveCommand(int x, int y, int panelWidth, int panelHeight) {
////        try {
////            client.send("control:mouseMove:" + x + "," + y + "," + panelWidth + "," + panelHeight);
////        } catch (Exception e) {
////            System.err.println("Error sending mouse move command: " + e.getMessage());
////        }
////    }
////
////    private static void sendMouseClickCommand(int x, int y, int panelWidth, int panelHeight) {
////        try {
////            client.send("control:mouseClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
////        } catch (Exception e) {
////            System.err.println("Error sending mouse click command: " + e.getMessage());
////        }
////    }
////
////    private static void sendMouseDoubleClickCommand(int x, int y, int panelWidth, int panelHeight) {
////        try {
////            client.send("control:mouseDoubleClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
////        } catch (Exception e) {
////            System.err.println("Error sending mouse double-click command: " + e.getMessage());
////        }
////    }
////
////    private static void sendRightClickCommand(int x, int y, int panelWidth, int panelHeight) {
////        try {
////            client.send("control:mouseRightClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
////        } catch (Exception e) {
////            System.err.println("Error sending right-click command: " + e.getMessage());
////        }
////    }
////
////    private static void sendMouseScrollCommand(int scrollAmount) {
////        try {
////            client.send("control:mouseScroll:" + scrollAmount);
////        } catch (Exception e) {
////            System.err.println("Error sending mouse scroll command: " + e.getMessage());
////        }
////    }
////
////    private static void sendKeyPressCommand(int keyCode) {
////        try {
////            client.send("control:keyPress:" + keyCode);
////        } catch (Exception e) {
////            System.err.println("Error sending key press command: " + e.getMessage());
////        }
////    }
////
////    private static void sendKeyReleaseCommand(int keyCode) {
////        try {
////            client.send("control:keyRelease:" + keyCode);
////        } catch (Exception e) {
////            System.err.println("Error sending key release command: " + e.getMessage());
////        }
////    }
////}
//
//import javax.swing.*;
//import java.awt.*;
//import java.awt.event.*;
//import java.awt.image.BufferedImage;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.net.URI;
//import java.nio.ByteBuffer;
//import java.util.concurrent.ConcurrentLinkedQueue;
//import javax.imageio.ImageIO;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//
//public class ViewerApplication1 {
//    private static final String BROKER_SERVER_URL = "ws://129.154.243.213:5000";
//    private static WebSocketClient client;
//    private static JFrame frame;
//    private static BufferedImage screenImage;
//    private static final ConcurrentLinkedQueue<byte[]> imageChunks = new ConcurrentLinkedQueue<>();
//    private static volatile boolean receivingImage = false;
//    private static volatile int expectedImageSize = 0;
//    private static volatile int receivedImageSize = 0;
//
//    public static void main(String[] args) {
//        try {
//            initializeWebSocketClient();
//            initializeViewerFrame();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void initializeWebSocketClient() throws Exception {
//        client = new WebSocketClient(new URI(BROKER_SERVER_URL)) {
//            @Override
//            public void onOpen(ServerHandshake handshake) {
//                System.out.println("Connected to broker as Viewer.");
//                client.send("viewer_connected");
//            }
//
//            @Override
//            public void onMessage(String message) {
//                if (message.startsWith("size:")) {
//                    expectedImageSize = Integer.parseInt(message.substring(5));
//                    imageChunks.clear();
//                    receivedImageSize = 0;
//                    receivingImage = true;
//                    System.out.println("Started receiving image of size: " + expectedImageSize);
//                } else if (message.equals("end")) {
//                    receivingImage = false;
//                    System.out.println("Finished receiving image, processing...");
//                    processReceivedImage();
//                }
//            }
//
//            @Override
//            public void onMessage(ByteBuffer message) {
//                if (receivingImage) {
//                    byte[] chunk = new byte[message.remaining()];
//                    message.get(chunk);
//                    imageChunks.add(chunk);
//                    receivedImageSize += chunk.length;
//                    System.out.println("Received binary chunk of size: " + chunk.length + ", total received: " + receivedImageSize);
//                }
//            }
//
//            @Override
//            public void onClose(int code, String reason, boolean remote) {
//                System.out.println("Disconnected from broker: " + reason);
//                receivingImage = false;
//            }
//
//            @Override
//            public void onError(Exception ex) {
//                System.err.println("WebSocket error: " + ex.getMessage());
//                ex.printStackTrace();
//            }
//        };
//
//        client.addHeader("clientType", "viewer");
//        client.connect();
//
//        while (!client.isOpen()) {
//            Thread.sleep(100);
//        }
//    }
//
//    private static void initializeViewerFrame() {
//        frame = new JFrame("Viewer Application");
//        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setSize(800, 600);
//        frame.setLocationRelativeTo(null);
//
//        JPanel panel = new JPanel() {
//            @Override
//            protected void paintComponent(Graphics g) {
//                super.paintComponent(g);
//                if (screenImage != null) {
//                    int width = getWidth();
//                    int height = getHeight();
//                    g.drawImage(screenImage, 0, 0, width, height, null);
//                }
//            }
//        };
//
//        panel.addMouseMotionListener(new MouseMotionAdapter() {
//            @Override
//            public void mouseMoved(MouseEvent e) {
//                sendMouseMoveCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
//            }
//        });
//
//        panel.addMouseListener(new MouseAdapter() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                if (e.getClickCount() == 2) {
//                    sendMouseDoubleClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
//                } else if (e.getButton() == MouseEvent.BUTTON1) {
//                    sendMouseClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
//                } else if (e.getButton() == MouseEvent.BUTTON3) { // Right-click
//                    sendRightClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
//                }
//            }
//        });
//
//        panel.addMouseWheelListener(e -> sendMouseScrollCommand(e.getWheelRotation()));
//
//        frame.addKeyListener(new KeyAdapter() {
//            @Override
//            public void keyPressed(KeyEvent e) {
//                sendKeyPressCommand(e.getKeyCode());
//            }
//
//            @Override
//            public void keyReleased(KeyEvent e) {
//                sendKeyReleaseCommand(e.getKeyCode());
//            }
//        });
//
//        frame.add(panel);
//        frame.setVisible(true);
//    }
//
//    private static void processReceivedImage() {
//        try {
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            for (byte[] chunk : imageChunks) {
//                baos.write(chunk);
//            }
//            byte[] imageData = baos.toByteArray();
//            
//            // Debug statement
//            System.out.println("Assembled image size: " + imageData.length);
//
//            if (imageData.length == expectedImageSize) {
//                screenImage = ImageIO.read(new ByteArrayInputStream(imageData));
//                frame.repaint();
//                System.out.println("Image displayed successfully.");
//            } else {
//                System.err.println("Image size mismatch: expected " + expectedImageSize + ", but got " + imageData.length);
//            }
//        } catch (Exception e) {
//            System.err.println("Error processing received image: " + e.getMessage());
//        }
//    }
//
//    private static void sendMouseMoveCommand(int x, int y, int panelWidth, int panelHeight) {
//        try {
//            client.send("control:mouseMove:" + x + "," + y + "," + panelWidth + "," + panelHeight);
//        } catch (Exception e) {
//            System.err.println("Error sending mouse move command: " + e.getMessage());
//        }
//    }
//
//    private static void sendMouseClickCommand(int x, int y, int panelWidth, int panelHeight) {
//        try {
//            client.send("control:mouseClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
//        } catch (Exception e) {
//            System.err.println("Error sending mouse click command: " + e.getMessage());
//        }
//    }
//
//    private static void sendMouseDoubleClickCommand(int x, int y, int panelWidth, int panelHeight) {
//        try {
//            client.send("control:mouseDoubleClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
//        } catch (Exception e) {
//            System.err.println("Error sending mouse double-click command: " + e.getMessage());
//        }
//    }
//
//    private static void sendRightClickCommand(int x, int y, int panelWidth, int panelHeight) {
//        try {
//            client.send("control:mouseRightClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
//        } catch (Exception e) {
//            System.err.println("Error sending right-click command: " + e.getMessage());
//        }
//    }
//
//    private static void sendMouseScrollCommand(int scrollAmount) {
//        try {
//            client.send("control:mouseScroll:" + scrollAmount);
//        } catch (Exception e) {
//            System.err.println("Error sending mouse scroll command: " + e.getMessage());
//        }
//    }
//
//    private static void sendKeyPressCommand(int keyCode) {
//        try {
//            client.send("control:keyPress:" + keyCode);
//        } catch (Exception e) {
//            System.err.println("Error sending key press command: " + e.getMessage());
//        }
//    }
//
//    private static void sendKeyReleaseCommand(int keyCode) {
//        try {
//            client.send("control:keyRelease:" + keyCode);
//        } catch (Exception e) {
//            System.err.println("Error sending key release command: " + e.getMessage());
//        }
//    }
//}


import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ViewerApplication1 {
    private static final String BROKER_SERVER_URL = "ws://129.154.243.213:5000";
    private static WebSocketClient client;
    private static JFrame frame;
    private static BufferedImage screenImage;
    private static final ConcurrentLinkedQueue<byte[]> imageChunks = new ConcurrentLinkedQueue<>();
    private static volatile boolean receivingImage = false;
    private static volatile int expectedImageSize = 0;
    private static volatile int receivedImageSize = 0;

    public static void main(String[] args) {
        try {
            initializeWebSocketClient();
            initializeViewerFrame();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initializeWebSocketClient() throws Exception {
        client = new WebSocketClient(new URI(BROKER_SERVER_URL)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to broker as Viewer.");
                client.send("viewer_connected");
            }

            @Override
            public void onMessage(String message) {
                System.out.println("Received message: " + message);
                if (message.startsWith("size:")) {
                    expectedImageSize = Integer.parseInt(message.substring(5));
                    imageChunks.clear();
                    receivedImageSize = 0;
                    receivingImage = true;
                    System.out.println("Started receiving image of size: " + expectedImageSize);
                } else if (message.equals("end")) {
                    receivingImage = false;
                    System.out.println("Finished receiving image, processing...");
                    processReceivedImage();
                }
            }

            @Override
            public void onMessage(ByteBuffer message) {
                System.out.println("Received binary message of size: " + message.remaining());
                if (receivingImage) {
                    byte[] chunk = new byte[message.remaining()];
                    message.get(chunk);
                    imageChunks.add(chunk);
                    receivedImageSize += chunk.length;
                    System.out.println("Received binary chunk of size: " + chunk.length + ", total received: " + receivedImageSize);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Disconnected from broker: " + reason);
                receivingImage = false;
            }

            @Override
            public void onError(Exception ex) {
                System.err.println("WebSocket error: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        client.addHeader("clientType", "viewer");
        client.connect();

        while (!client.isOpen()) {
            Thread.sleep(100);
        }
    }

    private static void initializeViewerFrame() {
        frame = new JFrame("Viewer Application");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (screenImage != null) {
                    int width = getWidth();
                    int height = getHeight();
                    g.drawImage(screenImage, 0, 0, width, height, null);
                }
            }
        };

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                sendMouseMoveCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    sendMouseDoubleClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
                } else if (e.getButton() == MouseEvent.BUTTON1) {
                    sendMouseClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
                } else if (e.getButton() == MouseEvent.BUTTON3) { // Right-click
                    sendRightClickCommand(e.getX(), e.getY(), panel.getWidth(), panel.getHeight());
                }
            }
        });

        panel.addMouseWheelListener(e -> sendMouseScrollCommand(e.getWheelRotation()));

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendKeyPressCommand(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                sendKeyReleaseCommand(e.getKeyCode());
            }
        });

        frame.add(panel);
        frame.setVisible(true);
    }

    private static void processReceivedImage() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (byte[] chunk : imageChunks) {
                baos.write(chunk);
            }
            byte[] imageData = baos.toByteArray();
            
            // Debug statement
            System.out.println("Assembled image size: " + imageData.length);

            if (imageData.length == expectedImageSize) {
                screenImage = ImageIO.read(new ByteArrayInputStream(imageData));
                frame.repaint();
                System.out.println("Image displayed successfully.");
            } else {
                System.err.println("Image size mismatch: expected " + expectedImageSize + ", but got " + imageData.length);
            }
        } catch (Exception e) {
            System.err.println("Error processing received image: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendMouseMoveCommand(int x, int y, int panelWidth, int panelHeight) {
        try {
            client.send("control:mouseMove:" + x + "," + y + "," + panelWidth + "," + panelHeight);
        } catch (Exception e) {
            System.err.println("Error sending mouse move command: " + e.getMessage());
        }
    }

    private static void sendMouseClickCommand(int x, int y, int panelWidth, int panelHeight) {
        try {
            client.send("control:mouseClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
        } catch (Exception e) {
            System.err.println("Error sending mouse click command: " + e.getMessage());
        }
    }

    private static void sendMouseDoubleClickCommand(int x, int y, int panelWidth, int panelHeight) {
        try {
            client.send("control:mouseDoubleClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
        } catch (Exception e) {
            System.err.println("Error sending mouse double-click command: " + e.getMessage());
        }
    }

    private static void sendRightClickCommand(int x, int y, int panelWidth, int panelHeight) {
        try {
            client.send("control:mouseRightClick:" + x + "," + y + "," + panelWidth + "," + panelHeight);
        } catch (Exception e) {
            System.err.println("Error sending right-click command: " + e.getMessage());
        }
    }

    private static void sendMouseScrollCommand(int scrollAmount) {
        try {
            client.send("control:mouseScroll:" + scrollAmount);
        } catch (Exception e) {
            System.err.println("Error sending mouse scroll command: " + e.getMessage());
        }
    }

    private static void sendKeyPressCommand(int keyCode) {
        try {
            client.send("control:keyPress:" + keyCode);
        } catch (Exception e) {
            System.err.println("Error sending key press command: " + e.getMessage());
        }
    }

    private static void sendKeyReleaseCommand(int keyCode) {
        try {
            client.send("control:keyRelease:" + keyCode);
        } catch (Exception e) {
            System.err.println("Error sending key release command: " + e.getMessage());
        }
    }
}