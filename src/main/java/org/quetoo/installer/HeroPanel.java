package org.quetoo.installer;

import io.reactivex.schedulers.Schedulers;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.quetoo.installer.aws.S3;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.quetoo.installer.aws.S3.getBoolean;
import static org.quetoo.installer.aws.S3.getChildNodes;
import static org.quetoo.installer.aws.S3.getString;

/**
 * A panel that displays hero images fetched from S3 with a crossfade transition.
 *
 * <p>Images are loaded asynchronously from the {@code quetoo/hero-images/} S3 prefix.
 * Once at least two images are available, the panel cycles through them every
 * {@value #DISPLAY_DURATION_MS}ms with a {@value #FADE_DURATION_MS}ms crossfade.</p>
 *
 * <p>Use {@link #beginLoading(CloseableHttpClient)} to start fetching images as early
 * as possible (before the Swing window is created), then pass the returned {@link Loader}
 * to the constructor.</p>
 */
public class HeroPanel extends JComponent {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final int DISPLAY_DURATION_MS = 5000;
  private static final int FADE_DURATION_MS = 2000;
  private static final int FADE_INTERVAL_MS = 1000 / 30;

  // 920px content width (960 window - 40px border) at 16:9
  private static final int HERO_WIDTH = 920;
  private static final int HERO_HEIGHT = HERO_WIDTH * 9 / 16;

  private final List<BufferedImage> images = new ArrayList<>();
  private int currentIndex = -1;
  private int nextIndex = -1;
  private float alpha = 1.0f;
  private boolean transitioning = false;

  private final Timer cycleTimer;
  private final Timer fadeTimer;

  /**
   * Starts loading hero images from S3 in the background. Call this as early as
   * possible (e.g. right after creating the {@link Config}) to overlap the HTTP
   * connection setup with Swing initialization.
   *
   * @param httpClient The HTTP client.
   * @return A {@link Loader} to pass to the {@link HeroPanel} constructor.
   */
  public static Loader beginLoading(final CloseableHttpClient httpClient) {
    final var loader = new Loader(httpClient);
    Schedulers.io().scheduleDirect(loader::run);
    return loader;
  }

  /**
   * Instantiates a {@link HeroPanel} that displays images from the given {@link Loader}.
   *
   * @param loader  The Loader (from {@link #beginLoading}).
   * @param onReady Invoked on the EDT once the first hero image is available.
   */
  public HeroPanel(final Loader loader, final Runnable onReady) {

    setPreferredSize(new Dimension(HERO_WIDTH, HERO_HEIGHT));

    cycleTimer = new Timer(DISPLAY_DURATION_MS, e -> startTransition());
    cycleTimer.setRepeats(true);

    fadeTimer = new Timer(FADE_INTERVAL_MS, e -> updateFade());
    fadeTimer.setRepeats(true);

    consumeLoader(loader, onReady);
  }

  /**
   * Waits for the first image from the loader on a background thread, then
   * delivers images to the EDT as they become available.
   */
  private void consumeLoader(final Loader loader, final Runnable onReady) {
    Schedulers.io().scheduleDirect(() -> {
      loader.awaitFirstImage();
      final var loaded = loader.getImages();
      int delivered = 0;

      // Deliver images already available
      for (; delivered < loaded.size(); delivered++) {
        final var image = loaded.get(delivered);
        SwingUtilities.invokeLater(() -> onImageLoaded(image));
      }
      SwingUtilities.invokeLater(onReady);

      // Continue delivering images as they arrive
      while (!loader.isComplete()) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        for (; delivered < loaded.size(); delivered++) {
          final var image = loaded.get(delivered);
          SwingUtilities.invokeLater(() -> onImageLoaded(image));
        }
      }

      // Deliver any final images
      for (; delivered < loaded.size(); delivered++) {
        final var image = loaded.get(delivered);
        SwingUtilities.invokeLater(() -> onImageLoaded(image));
      }
    });
  }

  /**
   * Called on the EDT when an image has been loaded.
   */
  private void onImageLoaded(final BufferedImage image) {
    images.add(image);

    if (images.size() == 1) {
      currentIndex = 0;
      repaint();
    }

    if (images.size() == 2) {
      cycleTimer.start();
    }
  }

  private void startTransition() {
    if (images.size() <= 1 || transitioning) return;

    nextIndex = (currentIndex + 1) % images.size();
    alpha = 0.0f;
    transitioning = true;
    fadeTimer.start();
  }

  private void updateFade() {
    alpha += (float) FADE_INTERVAL_MS / FADE_DURATION_MS;
    if (alpha >= 1.0f) {
      alpha = 1.0f;
      transitioning = false;
      currentIndex = nextIndex;
      nextIndex = -1;
      fadeTimer.stop();
    }
    repaint();
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (images.isEmpty() || currentIndex < 0) return;

    final var g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

    if (transitioning && nextIndex >= 0) {
      drawImage(g2, images.get(currentIndex), 1.0f - alpha);
      drawImage(g2, images.get(nextIndex), alpha);
    } else {
      drawImage(g2, images.get(currentIndex), 1.0f);
    }

    g2.dispose();
  }

  /**
   * Draws an image scaled to cover the panel area with the given opacity.
   */
  private void drawImage(final Graphics2D g2, final BufferedImage img, final float opacity) {
    final int pw = getWidth();
    final int ph = getHeight();

    final float scaleW = (float) pw / img.getWidth();
    final float scaleH = (float) ph / img.getHeight();
    final float scale = Math.max(scaleW, scaleH);

    final int w = (int) (img.getWidth() * scale);
    final int h = (int) (img.getHeight() * scale);
    final int x = (pw - w) / 2;
    final int y = (ph - h) / 2;

    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
    g2.drawImage(img, x, y, w, h, null);
  }

  /**
   * Loads hero images from S3 on a background thread. Thread-safe; images can be
   * consumed as they arrive via {@link #getImages()}.
   */
  public static class Loader implements Runnable {

    private final CloseableHttpClient httpClient;
    private final List<BufferedImage> images = new CopyOnWriteArrayList<>();
    private final CountDownLatch firstImage = new CountDownLatch(1);
    private volatile boolean complete;

    Loader(final CloseableHttpClient httpClient) {
      this.httpClient = httpClient;
    }

    @Override
    public void run() {
      try {
        final var keys = listImageKeys();
        for (final var key : keys) {
          try {
            final var image = downloadImage(key);
            if (image != null) {
              images.add(image);
              firstImage.countDown();
            }
          } catch (IOException e) {
            System.err.println("Failed to load hero image " + key + ": " + e.getMessage());
          }
        }
      } catch (IOException e) {
        System.err.println("Failed to list hero images: " + e.getMessage());
      }
      firstImage.countDown();
      complete = true;
    }

    /**
     * Blocks until the first image is available or loading fails.
     */
    void awaitFirstImage() {
      try {
        firstImage.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    List<BufferedImage> getImages() {
      return images;
    }

    boolean isComplete() {
      return complete;
    }

    private List<String> listImageKeys() throws IOException {
      final var imageKeys = new ArrayList<String>();
      String marker = null;

      while (true) {
        final var url = new StringBuilder("https://quetoo.s3.amazonaws.com/?prefix=hero-images/");
        if (marker != null) {
          url.append("&marker=").append(marker.replace("+", "%2B").replace(" ", "%20"));
        }

        final var doc = httpClient.execute(new HttpGet(URI.create(url.toString())), res ->
            S3.getDocument(res.getEntity().getContent())
        );

        final var allKeys = getChildNodes(doc.getDocumentElement(), "Contents")
            .map(node -> getString(node, "Key"))
            .toList();

        allKeys.stream()
            .filter(key -> {
              final var lower = key.toLowerCase(Locale.ROOT);
              return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
            })
            .forEach(imageKeys::add);

        if (!getBoolean(doc.getDocumentElement(), "IsTruncated") || allKeys.isEmpty()) {
          break;
        }

        marker = allKeys.get(allKeys.size() - 1);
      }

      return imageKeys;
    }

    private BufferedImage downloadImage(final String key) throws IOException {
      final var url = "https://quetoo.s3.amazonaws.com/" + key.replace("+", "%2B").replace(" ", "%20");
      return httpClient.execute(new HttpGet(URI.create(url)), res ->
          ImageIO.read(res.getEntity().getContent())
      );
    }
  }
}
