package org.quetoo.installer.aws;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Strings;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.quetoo.installer.Asset;
import org.quetoo.installer.Delta;
import org.quetoo.installer.Index;
import org.quetoo.installer.Sync;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Synchronizes a local file system destination with an S3 bucket.
 *
 * @author jdolan
 */
public class S3Sync implements Sync {

  private final CloseableHttpClient httpClient;
  private final String bucketName;
  private final Predicate<S3Object> predicate;
  private final Function<S3Object, File> mapper;
  private final File destination;
  /**
   * Instantiates an {@link S3Sync} with the given Builder.
   *
   * @param builder The Builder.
   */
  private S3Sync(final Builder builder) {

    httpClient = builder.httpClient;
    bucketName = builder.bucketName;
    predicate = builder.predicate;
    mapper = builder.mapper;
    destination = builder.destination;
  }

  /**
   * Executes an HTTP GET request for the specified path.
   *
   * @param path    The path.
   * @param params  The query parameters.
   * @param handler The response handler.
   * @return The parsed response.
   * @throws IOException If an error occurs.
   */
  private <T> T executeHttpRequest(final String path, final Map<String, String> params,
                                   final ResponseHandler<T> handler) throws IOException {

    final StringBuilder url = new StringBuilder()
        .append("https://")
        .append(bucketName)
        .append(".s3.amazonaws.com/")
        .append(path.replace("+", "%2B"));

    if (!params.isEmpty()) {
      url.append("?");
      params.forEach((k, v) -> {
        if (url.charAt(url.length() - 1) != '?') {
          url.append("&");
        }
        url.append(k).append("=").append(v.replace("+", "%2B"));
      });
    }

    try {
      return httpClient.execute(new HttpGet(URI.create(url.toString())), res -> {
        return handler.handleResponse(res.getEntity().getContent());
      });
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Performs a delta check for the given {@link S3Object}.
   *
   * @param obj The {@link S3Object}.
   * @return True if the object represents a delta, false otherwise.
   * @throws IOException If an error occurs.
   */
  private boolean delta(final S3Object obj) throws IOException {

    final File file = map(obj);
    if (file.exists()) {
      if (file.isDirectory() && obj.isDirectory()) {
        return false;
      }

      if (!file.isFile()) {
        return true;
      }

      // Multipart uploads have ETags like "md5-partcount", not a plain MD5
      if (obj.getEtag().contains("-")) {
        return file.length() != obj.getSize();
      }

      try (FileInputStream fis = new FileInputStream(file)) {
        final String md5 = DigestUtils.md5Hex(fis);
        if (Strings.CS.equals(md5, obj.getEtag())) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Synchronizes the given {@link S3Object}.
   *
   * @param obj The {@link S3Object}.
   * @return The resulting File.
   * @throws IOException If an error occurs.
   */
  private File sync(final S3Object obj) throws IOException {

    final var file = map(obj);
    if (file.exists()) {
      if (file.isDirectory() != obj.isDirectory()) {
        FileUtils.deleteQuietly(file);
      }
    }

    if (obj.isDirectory()) {
      FileUtils.forceMkdir(file);
    } else {
      FileUtils.forceMkdirParent(file);
      executeHttpRequest(obj.getKey(), Collections.emptyMap(), inputStream -> {
        try (FileOutputStream out = new FileOutputStream(file)) {
          return IOUtils.copy(inputStream, out);
        }
      });
    }

    return file;
  }

  @Override
  public File map(final Asset asset) {
    return new File(destination, mapper.apply((S3Object) asset).getPath());
  }

  @Override
  public Observable<Index> index() {
    return Observable.create(source -> {

      final Map<String, String> params = new HashMap<>();
      while (true) {

        final var bucket = new S3Bucket(this, executeHttpRequest("", params, S3::getDocument));
        final int rawCount = bucket.count();

        if (predicate != null) {
          params.put("marker", bucket.getMarker());
          source.onNext(bucket.filter(predicate));
        } else {
          params.put("marker", bucket.getMarker());
          source.onNext(bucket);
        }

        if (rawCount < 1000) {
          source.onComplete();
          break;
        }
      }
    });
  }

  @Override
  public Single<Delta> delta(final Index index) {
    return Single.just(index)
        .cast(S3Bucket.class)
        .flatMap(bucket -> {
          return Observable.fromIterable(bucket)
              .cast(S3Object.class)
              .filter(this::delta)
              .toList()
              .map(objects -> new S3Delta(bucket, objects));
        });
  }

  @Override
  public Observable<File> sync(final Delta delta) {
    return Observable.fromIterable(delta)
        .map(asset -> (S3Object) asset)
        .flatMap(obj -> Observable.fromCallable(() -> sync(obj))
            .subscribeOn(Schedulers.io()), 8);
  }

  @Override
  public void close() throws IOException {
    httpClient.close();
  }

  /**
   * A specialized ResponseHandler for conveniently dealing with response
   * InputStreams.
   *
   * @param <T> The parsed response object.
   */
  private interface ResponseHandler<T> {
    T handleResponse(final InputStream inputStream) throws IOException;
  }

  /**
   * A builder for creating {@link S3Sync} instances.
   */
  public static class Builder {

    private CloseableHttpClient httpClient;
    private String bucketName;
    private Predicate<S3Object> predicate;
    private Function<S3Object, File> mapper;
    private File destination;

    public Builder withHttpClient(final CloseableHttpClient httpClient) {
      this.httpClient = httpClient;
      return this;
    }

    public Builder withBucketName(final String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder withPredicate(final Predicate<S3Object> predicate) {
      this.predicate = predicate;
      return this;
    }

    public Builder withMapper(final Function<S3Object, File> mapper) {
      this.mapper = mapper;
      return this;
    }

    public Builder withDestination(final File destination) {
      this.destination = destination;
      return this;
    }

    public Builder withDestination(final String destination) {
      this.destination = new File(destination);
      return this;
    }

    public S3Sync build() {
      return new S3Sync(this);
    }
  }
}
