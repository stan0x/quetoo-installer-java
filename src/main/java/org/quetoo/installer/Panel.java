package org.quetoo.installer;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The primary container of the user interface.
 *
 * @author jdolan
 */
public class Panel extends JPanel {

  @Serial
  private static final long serialVersionUID = 1L;

  private final Manager manager;
  private final JProgressBar progressBar;
  private final JLabel status;
  private final JTextArea summary;
  private final JButton copySummary;
  private final JButton pruneButton;
  private final JButton playButton;

  private final List<Disposable> subscriptions = Collections.synchronizedList(new ArrayList<>());
  private final List<File> unknownAssets = Collections.synchronizedList(new ArrayList<>());

  /**
   * Instantiates a {@link Panel} with the specified {@link Manager}.
   *
   * @param manager The Manager.
   */
  public Panel(final Manager manager, final HeroPanel.Loader heroLoader) {

    super(new BorderLayout(0, 5), true);

    this.manager = manager;

    progressBar = new JProgressBar(0, 100);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);

    status = new JLabel(" ");

    summary = new JTextArea(10, 40);
    summary.setMargin(new Insets(5, 5, 5, 5));
    summary.setEditable(false);

    summary.append("Updating " + manager.getConfig().getDir() + "\n");

    final var caret = (DefaultCaret) summary.getCaret();
    caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

    {
      final var panel = new JPanel(new BorderLayout(0, 10));

      panel.add(new HeroPanel(heroLoader, this::update), BorderLayout.NORTH);

      final var statusPanel = new JPanel(new BorderLayout(0, 5));
      statusPanel.add(status, BorderLayout.NORTH);
      statusPanel.add(progressBar, BorderLayout.SOUTH);
      panel.add(statusPanel, BorderLayout.SOUTH);

      add(panel, BorderLayout.PAGE_START);
    }

    add(new JScrollPane(summary), BorderLayout.CENTER);

    {
      final var panel = new JPanel(new BorderLayout(0, 5));

      copySummary = new JButton("Copy Summary");
      copySummary.addActionListener(this::onCopySummary);

      pruneButton = new JButton("Prune");
      pruneButton.setEnabled(false);
      pruneButton.addActionListener(this::onPruneAction);

      playButton = new JButton("Play");
      playButton.setEnabled(false);
      playButton.addActionListener(this::onPlayAction);

      final var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
      buttons.add(copySummary);
      buttons.add(pruneButton);
      buttons.add(playButton);

      panel.add(buttons, BorderLayout.EAST);

      add(panel, BorderLayout.PAGE_END);
    }

    setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
  }

  /**
   * Cancels all pending subscriptions.
   */
  public void cancel() {
    subscriptions.forEach(Disposable::dispose);
    subscriptions.clear();
  }

  /**
   * Dispatches {@link Manager#sync(Observable)}.
   */
  public void update() {

    setStatus("Retrieving asset list for " + manager.getConfig().getBuild() + "..");

    progressBar.setValue(0);
    progressBar.setMaximum(0);
    progressBar.setIndeterminate(true);

    Schedulers.io().scheduleDirect(() -> {
      final var files = manager.sync(
          manager.delta(
                  manager.index()
                      .toList()
                      .doOnSuccess(this::onIndices)
                      .flatMapObservable(Observable::fromIterable)
              ).toList()
              .doOnSuccess(this::onDeltas)
              .flatMapObservable(Observable::fromIterable)
      ).observeOn(Schedulers.from(SwingUtilities::invokeLater));

      subscriptions.add(files.subscribe(this::onSync, this::onError, this::onComplete));
    });
  }

  /**
   * Sets the status to `string` and appends `string` to the summary.
   *
   * @param string The String to log.
   */
  private void setStatus(final String string) {

    status.setText(string);
    summary.append(string + "\n");
  }

  /**
   * Called when all indices are available.
   *
   * @param indices The indices.
   */
  private void onIndices(final List<Index> indices) {

    final var count = indices.stream().mapToInt(Index::count).sum();
    setStatus("Calculating update for " + count + " assets");
  }

  /**
   * Called when the deltas are available.
   *
   * @param deltas The deltas.
   */
  private void onDeltas(final List<Delta> deltas) {

    final var count = deltas.stream().mapToInt(Delta::count).sum();
    final var size = deltas.stream().mapToLong(Delta::size).sum();

    final var megabytes = new BigDecimal(size / 1024.0 / 1024.0).setScale(2, RoundingMode.UP);

    setStatus("Updating " + count + " assets, " + megabytes + "MB");

    progressBar.setIndeterminate(false);
    progressBar.setMaximum(Math.max((int) size, 1));
  }

  /**
   * Called when each File is synchronized.
   *
   * @param file The File.
   */
  private void onSync(final File file) {

    progressBar.setValue(progressBar.getValue() + (int) file.length());

    final var dir = manager.getConfig().getDir() + File.separator;
    final var filename = file.toString().replace(dir, "");

    setStatus(filename);
  }

  /**
   * Called when an error occurs.
   *
   * @param throwable The error.
   */
  private void onError(final Throwable throwable) {

    status.setText(throwable.getMessage());

    final var stackTrace = new StringWriter();
    throwable.printStackTrace(new PrintWriter(stackTrace));

    summary.append(stackTrace.toString());
  }

  /**
   * Called when the sync operation completes successfully.
   */
  private void onComplete() {

    setStatus("Update complete");

    progressBar.setValue(progressBar.getMaximum());

    unknownAssets.clear();

    Schedulers.io().scheduleDirect(() -> {
      final var prune = manager.prune()
          .observeOn(Schedulers.from(SwingUtilities::invokeLater))
          .subscribe(this::onPrune, this::onError, this::onPruneComplete);
      subscriptions.add(prune);
    });
  }

  /**
   * Called when each File is pruned.
   *
   * @param file The File.
   */
  private void onPrune(final File file) {

    unknownAssets.add(file);

    final var dir = manager.getConfig().getDir() + File.separator;
    final var filename = file.toString().replace(dir, "");

    if (manager.getConfig().getPrune()) {
      setStatus("Removed unknown asset " + filename);
    } else {
      setStatus("Unknown asset " + filename);
    }
  }

  /**
   * Called when prune discovery completes, enabling the Prune button if unknown assets were found.
   */
  private void onPruneComplete() {
    if (!unknownAssets.isEmpty() && !manager.getConfig().getPrune()) {
      pruneButton.setEnabled(true);
    }
    playButton.setEnabled(true);
  }

  /**
   * Deletes unknown assets discovered during the prune phase.
   */
  private void onPruneAction(final ActionEvent e) {

    pruneButton.setEnabled(false);

    for (final var file : unknownAssets) {
      FileUtils.deleteQuietly(file);

      final var dir = manager.getConfig().getDir() + File.separator;
      final var filename = file.toString().replace(dir, "");

      setStatus("Removed " + filename);
    }

    unknownAssets.clear();

    setStatus("Prune complete");
  }

  /**
   * Copies the contents of `summary` to the clipboard.
   */
  private void onCopySummary(final ActionEvent e) {
    final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(new StringSelection(summary.getText()), null);
  }

  /**
   * Launches the Quetoo game executable.
   */
  private void onPlayAction(final ActionEvent e) {
    try {
      final var config = manager.getConfig();
      final ProcessBuilder pb = switch (config.getBuild()) {
        case arm64_apple_darwin -> new ProcessBuilder("open", config.getDir().getAbsolutePath());
        case x86_64_pc_linux -> new ProcessBuilder(new File(config.getBin(), "quetoo").getAbsolutePath());
        case x86_64_pc_windows -> new ProcessBuilder(new File(config.getBin(), "quetoo.exe").getAbsolutePath());
      };
      pb.start();
    } catch (IOException ex) {
      setStatus("Failed to launch: " + ex.getMessage());
    }
  }
}
