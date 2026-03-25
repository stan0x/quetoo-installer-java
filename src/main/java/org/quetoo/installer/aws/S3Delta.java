package org.quetoo.installer.aws;

import org.quetoo.installer.Asset;
import org.quetoo.installer.Delta;
import org.quetoo.installer.Index;

import java.util.Iterator;
import java.util.List;

/**
 * An abstraction for the {@link Delta} contents of an {@link S3Bucket}.
 *
 * @author jdolan
 */
public record S3Delta(S3Bucket bucket, List<S3Object> objects) implements Delta {

  /**
   * Instantiates an {@link S3Delta} with the given {@link S3Bucket} and {@link S3Object}s.
   *
   * @param bucket  The {@link S3Bucket}.
   * @param objects The delta {@link S3Object}s.
   */
  public S3Delta {
  }

  @Override
  public Iterator<Asset> iterator() {
    return objects.stream().map(obj -> (Asset) obj).iterator();
  }

  @Override
  public int count() {
    return objects().size();
  }

  @Override
  public long size() {
    return objects().stream().mapToLong(asset -> asset.size()).sum();
  }

  @Override
  public Index getIndex() {
    return bucket();
  }
}
