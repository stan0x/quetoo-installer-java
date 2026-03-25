package org.quetoo.installer;

import io.reactivex.Observable;

import java.io.File;
import java.util.List;

/**
 * The console user interface.
 *
 * @author jdolan
 */
public class Console {

  private final Manager manager;

  /**
   * Instantiates a Console to dispatch and await the given {@link Manager}.
   *
   * @param manager The {@link Manager}.
   */
  public Console(final Manager manager) {
    this.manager = manager;
    update();
  }

  /**
   * Dispatches {@link Manager#sync(Observable))}.
   */
  private void update() {

    System.out.println("Updating " + manager.getConfig().getDir());

    final var files = manager.sync(
        manager.delta(
                manager.index()
                    .toList()
                    .doOnSuccess(this::onIndices)
                    .flatMapObservable(Observable::fromIterable)
            ).toList()
            .doOnSuccess(this::onDeltas)
            .flatMapObservable(Observable::fromIterable)
    );

    files.blockingSubscribe(this::onSync, this::onError, this::onComplete);
  }

  /**
   * Called when all indices are available.
   *
   * @param indices The indices.
   */
  private void onIndices(final List<Index> indices) {

    final var count = indices.stream().mapToInt(Index::count).sum();
    System.out.println("Calculating update for " + count + " assets");
  }

  /**
   * Called when the deltas are available.
   *
   * @param deltas The deltas.
   */
  private void onDeltas(final List<Delta> deltas) {

    final var count = deltas.stream().mapToInt(Delta::count).sum();
    final var size = deltas.stream().mapToLong(Delta::size).sum();

    System.out.println("Updating " + count + " assets, " + size + " bytes");
  }

  /**
   * Called when each File is synchronized.
   *
   * @param file The File.
   */
  private void onSync(final File file) {

    final var dir = manager.getConfig().getDir() + File.separator;
    final var filename = file.toString().replace(dir, "");

    System.out.println(filename);
  }

  /**
   * Called when an error occurs.
   *
   * @param throwable The error.
   */
  private void onError(final Throwable throwable) {
    System.err.println("Error: " + throwable.getMessage());
    throwable.printStackTrace(System.err);
  }

  /**
   * Called when the sync operation completes successfully.
   */
  private void onComplete() {

    System.out.println("Update complete");

    manager.prune().subscribe(this::onPrune, this::onError);
  }

  /**
   * Called when each File is pruned.
   *
   * @param file The File.
   */
  private void onPrune(final File file) {

    final var dir = manager.getConfig().getDir() + File.separator;
    final var filename = file.toString().replace(dir, "");

    if (manager.getConfig().getPrune()) {
      System.out.println("Removed unknown asset " + filename);
    } else {
      System.out.println("Unknown asset " + filename);
    }
  }
}
