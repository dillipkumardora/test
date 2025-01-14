import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class HostApplication1 {
    private static final String BROKER_SERVER_URL = "ws://129.154.243.213:5000";
    private static Robot robot;
    private static WebSocketClient client;
    private static Rectangle screenRect;
    private static volatile boolean isViewerConnected = false;
    private static final int CHUNK_SIZE = 32 * 1024; // 32KB chunks
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    public static void main(String[] args) {
        try {
            // Enable full screen capture including taskbar
            System.setProperty("java.awt.headless", "false");
            System.setProperty("sun.java2d.d3d", "false");
            
            initializeWebSocketClient();
            initializeRobot();
            startScreenCapture();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void initializeWebSocketClient() throws Exception {
        client = new WebSocketClient(new URI(BROKER_SERVER_URL)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                System.out.println("Connected to broker as Host");
            }

            @Override
            public void onMessage(String message) {
                if (message.equals("viewer_connected")) {
                    isViewerConnected = true;
                    System.out.println("Viewer connected, starting screen capture");
                } else if (message.startsWith("control:")) {
                    processControlCommands(message.substring(8));
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("Disconnected from broker: " + reason);
                isViewerConnected = false;
                scheduler.schedule(() -> {
                    try {
                        client.reconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, 5, TimeUnit.SECONDS);
            }

            @Override
            public void onError(Exception ex) {
                System.err.println("WebSocket error: " + ex.getMessage());
                ex.printStackTrace();
            }
        };

        client.addHeader("clientType", "host");
        client.connect();

        while (!client.isOpen()) {
            Thread.sleep(100);
        }
    }

    private static void initializeRobot() throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(0);
        
        // Get the full screen size including all monitors
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        
        // Calculate the bounds that encompass all screens
        Rectangle totalBounds = new Rectangle();
        for (GraphicsDevice screen : screens) {
            Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();
            totalBounds = totalBounds.union(screenBounds);
        }
        
        screenRect = totalBounds;
        System.out.println("Capturing total screen area: " + screenRect.width + "x" + screenRect.height);
    }

    private static void startScreenCapture() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isViewerConnected && client.isOpen()) {
                    BufferedImage screenshot = captureScreen();
                    byte[] imageData = compressImage(screenshot);
                    sendImageData(imageData);
                }
            } catch (Exception e) {
                System.err.println("Error in screen capture: " + e.getMessage());
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // 20 FPS for smoother display
    }

    private static BufferedImage captureScreen() {
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        
        // Scale down the image while maintaining quality
        int maxWidth = 1920; // Max width for performance
        double scale = Math.min(1.0, (double) maxWidth / screenshot.getWidth());
        
        int newWidth = (int) (screenshot.getWidth() * scale);
        int newHeight = (int) (screenshot.getHeight() * scale);
        BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaledImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.drawImage(screenshot, 0, 0, newWidth, newHeight, null);
        g2d.dispose();
        
        return scaledImage;
    }

    private static byte[] compressImage(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(0.7f); // Higher quality for better readability
        
        try (MemoryCacheImageOutputStream outputStream = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(outputStream);
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        
        return baos.toByteArray();
    }

    private static void sendImageData(byte[] imageData) {
        try {
            // Send the image size first
            client.send("size:" + imageData.length);
            System.out.println("Sending image of size: " + imageData.length);
            // Send image data in chunks
            for (int offset = 0; offset < imageData.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, imageData.length - offset);
                ByteBuffer chunk = ByteBuffer.wrap(imageData, offset, length);
                client.send(chunk);
                
                // Debug statement
                System.out.println("Sent chunk of size: " + length);
                // Small delay between chunks to prevent overwhelming the connection
                Thread.sleep(1);
            }
            
            // Send end marker
            client.send("end");
            System.out.println("Image data sent completely.");
        } catch (Exception e) {
            System.err.println("Error sending image: " + e.getMessage());
        }
    }

    private static void processControlCommands(String command) {
        try {
            String[] parts = command.split(":");
            if (parts.length != 2) return;

            switch (parts[0]) {
                case "mouseMove":
                    String[] coords = parts[1].split(",");
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    double scaleX = (double) screenRect.width / Double.parseDouble(coords[2]);
                    double scaleY = (double) screenRect.height / Double.parseDouble(coords[3]);
                    robot.mouseMove((int)(x * scaleX), (int)(y * scaleY));
                    break;
                    
                case "mouseClick":
                    coords = parts[1].split(",");
                    x = Integer.parseInt(coords[0]);
                    y = Integer.parseInt(coords[1]);
                    scaleX = (double) screenRect.width / Double.parseDouble(coords[2]);
                    scaleY = (double) screenRect.height / Double.parseDouble(coords[3]);
                    robot.mouseMove((int)(x * scaleX), (int)(y * scaleY));
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.delay(50);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    break;

                case "mouseDoubleClick":
                    coords = parts[1].split(",");
                    x = Integer.parseInt(coords[0]);
                    y = Integer.parseInt(coords[1]);
                    scaleX = (double) screenRect.width / Double.parseDouble(coords[2]);
                    scaleY = (double) screenRect.height / Double.parseDouble(coords[3]);
                    robot.mouseMove((int)(x * scaleX), (int)(y * scaleY));
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.delay(50);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    robot.delay(50);
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                    robot.delay(50);
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                    break;
                    
                case "mouseScroll":
                    int scrollAmount = Integer.parseInt(parts[1]);
                    robot.mouseWheel(scrollAmount);
                    break;
                    
                case "mouseRightClick":
                    coords = parts[1].split(",");
                    x = Integer.parseInt(coords[0]);
                    y = Integer.parseInt(coords[1]);
                    scaleX = (double) screenRect.width / Double.parseDouble(coords[2]);
                    scaleY = (double) screenRect.height / Double.parseDouble(coords[3]);
                    robot.mouseMove((int)(x * scaleX), (int)(y * scaleY));
                    robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                    robot.delay(50);
                    robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                    break;
                    
                case "keyPress":
                    int keyCode = Integer.parseInt(parts[1]);
                    robot.keyPress(keyCode);
                    robot.keyRelease(keyCode);
                    break;

                default:
                    System.err.println("Unknown control command: " + command);
            }
        } catch (Exception e) {
            System.err.println("Error processing control command: " + e.getMessage());
        }
    }
}