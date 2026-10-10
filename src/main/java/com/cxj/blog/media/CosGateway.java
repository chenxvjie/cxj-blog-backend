package com.cxj.blog.media;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public class CosGateway implements AutoCloseable {
  private final COSClient client;
  private final String bucket;
  public CosGateway(COSClient client, String bucket) { this.client=client; this.bucket=bucket; }
  public record Metadata(long size, String type, String etag) {}

  public String uploadUrl(String key, Instant expires, Map<String,String> headers) {
    // Sign the exact size, MIME type, MD5 and forbid-overwrite header as well as Host.
    return client.generatePresignedUrl(bucket, key, Date.from(expires), HttpMethodName.PUT,
        headers, Map.of(), false, true).toString();
  }
  public Metadata head(String key) {
    var object=client.getObjectMetadata(bucket,key);
    return new Metadata(object.getContentLength(),object.getContentType(),object.getETag());
  }
  public void delete(String key) { client.deleteObject(bucket,key); }
  @Override public void close() { if (client != null) client.shutdown(); }
}
